/**
 * Precompiled [celeritas.platform-conventions.gradle.kts][Celeritas_platform_conventions_gradle] script plugin.
 *
 * @see Celeritas_platform_conventions_gradle
 */
public
class Celeritas_platformConventionsPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Celeritas_platform_conventions_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
