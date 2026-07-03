/**
 * Precompiled [celeritas.shader-conventions.gradle.kts][Celeritas_shader_conventions_gradle] script plugin.
 *
 * @see Celeritas_shader_conventions_gradle
 */
public
class Celeritas_shaderConventionsPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Celeritas_shader_conventions_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
