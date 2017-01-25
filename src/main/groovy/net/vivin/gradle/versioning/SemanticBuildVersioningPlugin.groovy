package net.vivin.gradle.versioning

import net.vivin.gradle.versioning.tasks.TagTask
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class SemanticBuildVersioningPlugin implements Plugin<Settings> {
    private final Logger logger = Logging.getLogger('semantic-build-versioning')

    @Override
    void apply(Settings settings) {
        settings.gradle.allprojects { project ->
            def configFile = project.file('semantic-build-versioning.gradle')

            // if no config file is present, don't apply semantic versioning
            if(!configFile.file) {
                return
            }

            SemanticBuildVersion semanticBuildVersion = new SemanticBuildVersion(project)

            semanticBuildVersion.snapshot = !project.hasProperty('release')
            semanticBuildVersion.newPreRelease = project.hasProperty('newPreRelease')
            semanticBuildVersion.promoteToRelease = project.hasProperty('promoteToRelease')
            semanticBuildVersion.forceBump = project.hasProperty('forceBump')

            if(project.hasProperty('bumpComponent')) {
                semanticBuildVersion.bump = VersionComponent."${project.bumpComponent.toUpperCase().replace '-', '_'}"
            }

            if(project.hasProperty('autobump')) {
                logger.warn 'The property "autobump" is deprecated and will be ignored'
            }

            semanticBuildVersion.config = new ConfigSlurper().parse(configFile.toURI().toURL())
            semanticBuildVersion.config.validate()

            project.version = semanticBuildVersion as String
            project.version.metaClass.snapshot = semanticBuildVersion.snapshot
            def versionComponents = project.version.split(/[.-]/, 4)
            project.version.metaClass.major = versionComponents[VersionComponent.MAJOR.index] as int
            project.version.metaClass.minor = versionComponents[VersionComponent.MINOR.index] as int
            project.version.metaClass.patch = versionComponents[VersionComponent.PATCH.index] as int
            if(versionComponents.size() == 4) {
                if(semanticBuildVersion.snapshot) {
                    project.version.metaClass.preRelease = versionComponents[VersionComponent.PRE_RELEASE.index] - ~/-$semanticBuildVersion.config.snapshotSuffix$/
                } else {
                    project.version.metaClass.preRelease = versionComponents[VersionComponent.PRE_RELEASE.index]
                }
            } else {
                project.version.metaClass.preRelease = null
            }
            project.ext.hasUncommittedChanges = semanticBuildVersion.versionUtils.&hasUncommittedChanges

            project.task('tag', type: TagTask, group: 'versioning') {
                onlyIf { !project.gradle.taskGraph.hasTask(project.tasks.tagAndPush) }
                tagPrefix semanticBuildVersion.config.tagPrefix
            }

            project.task('tagAndPush', type: TagTask, group: 'versioning') {
                tagPrefix semanticBuildVersion.config.tagPrefix
                push true
            }

            project.task('printVersion') << {
                logger.quiet project.version as String
            }

            project.tasks.all {
                if(name == 'release') {
                    it << {
                        logger.lifecycle "Releasing '$project.name' with version '$project.version'"
                    }
                }
            }
        }
    }
}
