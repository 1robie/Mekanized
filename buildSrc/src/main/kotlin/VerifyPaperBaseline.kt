import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.util.zip.ZipFile

/**
 * D25: modules compile against the newest Paper API but must run on the oldest supported one. Recompiling
 * the sources against 1.20.6 is not enough: types such as `Sound` or `Attribute` are enums in 1.20.6 and
 * interfaces in 26.x, so the same source compiles both ways but `sound.name()` compiled against 26.x is an
 * interface call that fails on 1.20.6 (Paper only rewrites old plugins for new servers, not the reverse).
 *
 * This task reads the compiled classes and checks every reference into [checkedPackages] against the
 * [baseline] API: the class exists and is a class or an interface as the call expects, and the method or
 * field exists with the same static-ness. It also checks that no class is newer than [maxClassVersion].
 *
 * [classes] may also hold jars: bundled libraries are checked the same way. A library may contain code that
 * cannot run on the baseline as long as we never call it; those problems go in [allowed], exactly as the
 * report prints them. An allowed problem of a class this task read that is no longer found fails too, so the
 * list stays true when a library is upgraded; entries for classes it did not read are ignored, so one list
 * serves every module.
 */
@CacheableTask
abstract class VerifyPaperBaseline : DefaultTask() {
    /** Compiled class directories of the module, or jars of bundled libraries. */
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection

    /** The oldest supported Paper API and everything it brings (Adventure, Guava, ...). */
    @get:Classpath
    abstract val baseline: ConfigurableFileCollection

    /** Internal-name prefixes provided by the server, e.g. `org/bukkit/`. */
    @get:Input
    abstract val checkedPackages: ListProperty<String>

    /** Highest allowed class file major version (65 = Java 21). */
    @get:Input
    abstract val maxClassVersion: Property<Int>

    /** Problems known to be unreachable from our code, exactly as reported. */
    @get:Input
    abstract val allowed: ListProperty<String>

    init {
        allowed.convention(emptyList())
    }

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val api = ApiIndex(baseline.files)
        val prefixes = checkedPackages.get()
        val problems = sortedSetOf<String>()
        val checkedClasses = HashSet<String>()
        fun check(name: String, bytes: () -> ByteArray) {
            if (!name.endsWith(".class") || name.endsWith("module-info.class") || name.startsWith("META-INF/")) return
            val reader = ClassReader(bytes())
            checkedClasses += reader.className.replace('/', '.')
            reader.accept(Checker(api, prefixes, maxClassVersion.get(), problems), 0)
        }
        classes.files.forEach { file ->
            if (file.isDirectory) {
                file.walk().filter { it.isFile }.forEach { check(it.relativeTo(file).invariantSeparatorsPath, it::readBytes) }
            } else if (file.isFile && file.name.endsWith(".jar")) {
                ZipFile(file).use { zip ->
                    for (entry in zip.entries()) check(entry.name) { zip.getInputStream(entry).use { it.readBytes() } }
                }
            }
        }
        val allowedProblems = allowed.get().toSet()
        val output = report.get().asFile
        // Entries for classes this task did not read belong to another module's libraries.
        val stale = allowedProblems.filter { it.substringBefore(':') in checkedClasses && it !in problems }
        if (stale.isNotEmpty()) {
            output.writeText(stale.joinToString("\n", postfix = "\n"))
            throw GradleException(
                "${stale.size} allowed baseline problem(s) are no longer found; remove them from the allow-list:\n" +
                        stale.joinToString("\n") { "  $it" }
            )
        }
        val allowedFound = problems.count { it in allowedProblems }
        problems.removeAll(allowedProblems)
        if (problems.isEmpty()) {
            val note = if (allowedFound == 0) "" else " ($allowedFound known problem(s) allowed)"
            output.writeText("${checkedClasses.size} classes match the baseline Paper API$note.\n")
            return
        }
        output.writeText(problems.joinToString("\n", postfix = "\n"))
        val shown = problems.take(40)
        throw GradleException(
            "${problems.size} reference(s) would fail on the oldest supported Paper version (D25). " +
                    "Move version-specific code behind nms:common.\n" +
                    shown.joinToString("\n") { "  $it" } +
                    (if (problems.size > shown.size) "\n  ... see ${output.path}" else "")
        )
    }
}

/** Members of the baseline API classes, plus JDK classes for supertypes such as `java/lang/Enum`. */
private class ApiIndex(jars: Set<File>) {
    class Info(val access: Int, val superName: String?, val interfaces: List<String>) {
        val methods = HashMap<String, Int>()
        val fields = HashMap<String, Int>()
        val isInterface get() = access and Opcodes.ACC_INTERFACE != 0
    }

    private val classes = HashMap<String, Info?>()

    init {
        jars.filter { it.isFile && it.name.endsWith(".jar") }.forEach { jar ->
            ZipFile(jar).use { zip ->
                for (entry in zip.entries()) {
                    val name = entry.name
                    if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.endsWith("module-info.class")) continue
                    zip.getInputStream(entry).use { read(it.readBytes()) }
                }
            }
        }
    }

    private fun read(bytes: ByteArray): Info? {
        var info: Info? = null
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
                info = Info(access, superName, interfaces?.toList().orEmpty()).also { classes[name] = it }
            }

            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
                info!!.methods[name + descriptor] = access
                return null
            }

            override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                info!!.fields[name + descriptor] = access
                return null
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return info
    }

    fun get(name: String): Info? = classes.getOrPut(name) {
        if (!name.startsWith("java/") && !name.startsWith("javax/")) return null
        jdkClass(name)
    }

    /** JDK classes come from reflection: the JDK running Gradle may be newer than ASM can read. */
    private fun jdkClass(name: String): Info? {
        val type = runCatching { Class.forName(name.replace('/', '.'), false, ClassLoader.getPlatformClassLoader()) }.getOrNull()
            ?: return null
        val info = Info(type.modifiers, type.superclass?.let(Type::getInternalName), type.interfaces.map(Type::getInternalName))
        type.declaredMethods.forEach { info.methods[it.name + Type.getMethodDescriptor(it)] = it.modifiers }
        type.declaredConstructors.forEach { info.methods["<init>" + Type.getConstructorDescriptor(it)] = it.modifiers }
        type.declaredFields.forEach { info.fields[it.name + Type.getDescriptor(it.type)] = it.modifiers }
        return info
    }

    /** Access flags of the method as the JVM resolves it (class chain, then superinterfaces), or null. */
    fun method(owner: String, nameAndDescriptor: String): Int? {
        val seen = HashSet<String>()
        var current: String? = owner
        val interfaces = ArrayDeque<String>()
        while (current != null) {
            val info = get(current) ?: return null
            info.methods[nameAndDescriptor]?.let { return it }
            interfaces.addAll(info.interfaces)
            current = info.superName
        }
        while (interfaces.isNotEmpty()) {
            val name = interfaces.removeFirst()
            if (!seen.add(name)) continue
            val info = get(name) ?: continue
            info.methods[nameAndDescriptor]?.let { return it }
            interfaces.addAll(info.interfaces)
        }
        return null
    }

    /** Access flags of the field as the JVM resolves it (class, its superinterfaces, then superclass), or null. */
    fun field(owner: String, nameAndDescriptor: String): Int? {
        val info = get(owner) ?: return null
        info.fields[nameAndDescriptor]?.let { return it }
        for (superInterface in info.interfaces) field(superInterface, nameAndDescriptor)?.let { return it }
        return info.superName?.let { field(it, nameAndDescriptor) }
    }
}

private class Checker(
    private val api: ApiIndex,
    private val prefixes: List<String>,
    private val maxVersion: Int,
    private val problems: MutableSet<String>,
) : ClassVisitor(Opcodes.ASM9) {
    private lateinit var className: String

    private fun checked(name: String) = prefixes.any { name.startsWith(it) }

    private fun report(message: String) {
        problems += "${className.replace('/', '.')}: $message"
    }

    private fun dotted(name: String) = name.replace('/', '.')

    /** The element class of an internal name or array descriptor, or null for primitives. */
    private fun elementClass(name: String): String? {
        if (!name.startsWith("[")) return name
        val element = Type.getType(name).elementType
        return if (element.sort == Type.OBJECT) element.internalName else null
    }

    private fun checkClass(name: String): ApiIndex.Info? {
        val element = elementClass(name) ?: return null
        if (!checked(element)) return null
        return api.get(element) ?: run {
            report("${dotted(element)} does not exist")
            null
        }
    }

    private fun checkDescriptor(descriptor: String) {
        val type = Type.getType(descriptor)
        val types = if (type.sort == Type.METHOD) type.argumentTypes.toList() + type.returnType else listOf(type)
        for (it in types) {
            if (it.sort == Type.OBJECT) checkClass(it.internalName) else if (it.sort == Type.ARRAY) checkClass(it.descriptor)
        }
    }

    private fun checkMethod(owner: String, name: String, descriptor: String, isInterface: Boolean, isStatic: Boolean) {
        checkDescriptor(descriptor)
        if (owner.startsWith("[")) {
            checkClass(owner) // clone() on an array
            return
        }
        val info = checkClass(owner) ?: return
        val member = "${dotted(owner)}.$name$descriptor"
        if (info.isInterface != isInterface) {
            report("$member is called as ${kind(isInterface)} method, but ${dotted(owner)} is ${kind(info.isInterface)}")
        }
        val access = api.method(owner, name + descriptor)
        if (access == null) report("$member does not exist")
        else if ((access and Opcodes.ACC_STATIC != 0) != isStatic) report("$member is ${if (isStatic) "not " else ""}static")
    }

    private fun checkField(owner: String, name: String, descriptor: String, isStatic: Boolean) {
        checkDescriptor(descriptor)
        checkClass(owner) ?: return
        val member = "${dotted(owner)}.$name:$descriptor"
        val access = api.field(owner, name + descriptor)
        if (access == null) report("$member does not exist")
        else if ((access and Opcodes.ACC_STATIC != 0) != isStatic) report("$member is ${if (isStatic) "not " else ""}static")
    }

    private fun kind(isInterface: Boolean) = if (isInterface) "an interface" else "a class"

    private fun checkHandle(handle: Handle) = when (handle.tag) {
        Opcodes.H_GETFIELD, Opcodes.H_PUTFIELD -> checkField(handle.owner, handle.name, handle.desc, false)
        Opcodes.H_GETSTATIC, Opcodes.H_PUTSTATIC -> checkField(handle.owner, handle.name, handle.desc, true)
        else -> checkMethod(handle.owner, handle.name, handle.desc, handle.isInterface, handle.tag == Opcodes.H_INVOKESTATIC)
    }

    private fun checkConstant(value: Any?) {
        when (value) {
            is Type -> checkDescriptor(value.descriptor)
            is Handle -> checkHandle(value)
            is ConstantDynamic -> {
                checkDescriptor(value.descriptor)
                checkHandle(value.bootstrapMethod)
                for (i in 0 until value.bootstrapMethodArgumentCount) checkConstant(value.getBootstrapMethodArgument(i))
            }
        }
    }

    override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
        className = name
        val major = version and 0xFFFF
        if (major > maxVersion) report("class file version $major is newer than $maxVersion")
        superName?.let { checkClass(it)?.let { info -> if (info.isInterface) report("extends ${dotted(it)}, an interface in the baseline") } }
        interfaces?.forEach { checkClass(it)?.let { info -> if (!info.isInterface) report("implements ${dotted(it)}, a class in the baseline") } }
    }

    override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
        checkDescriptor(descriptor)
        return null
    }

    override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor {
        checkDescriptor(descriptor)
        exceptions?.forEach { checkClass(it) }
        return object : MethodVisitor(Opcodes.ASM9) {
            override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) =
                checkMethod(owner, name, descriptor, isInterface, opcode == Opcodes.INVOKESTATIC)

            override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) =
                checkField(owner, name, descriptor, opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC)

            override fun visitTypeInsn(opcode: Int, type: String) {
                val info = checkClass(type)
                if (opcode == Opcodes.NEW && info != null && info.access and (Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT) != 0) {
                    report("creates ${dotted(type)}, which is abstract in the baseline")
                }
            }

            override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) = checkDescriptor(descriptor)

            override fun visitLdcInsn(value: Any?) = checkConstant(value)

            override fun visitInvokeDynamicInsn(name: String, descriptor: String, bootstrapMethodHandle: Handle, vararg bootstrapMethodArguments: Any?) {
                checkDescriptor(descriptor)
                checkHandle(bootstrapMethodHandle)
                bootstrapMethodArguments.forEach { checkConstant(it) }
            }

            override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
                type?.let { checkClass(it) }
            }
        }
    }
}
