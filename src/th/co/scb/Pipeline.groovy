#!/usr/bin/env groovy
package th.co.scb

class Pipeline implements Serializable {

    def script
    Map config
    PipelineContext context
    PipelineUtility util

    // script is the context of the Jenkinsfile. That means that things like "sh"
    // need to be called on script.
    // config is a map of config properties to customise the behaviour.
    Pipeline(script, config, PipelineUtility util) {
        this.script = script
        this.context = new PipelineContext(script, config, util)
        this.util = util
        this.config = config
    }

    // Main entry point.
    def execute(Closure stages) {
        util.info "***** Starting Pipeline *****"

        script.node(context.jenkinsAgentNode) {

            script.ansiColor('xterm') {
                try {
                    script.checkout(script.scm)
                    script.stage('Prepare') {
                        context.assemble()
                        util.slack.notify(context, 'STARTED')
                    }

                    stages(context)

                } catch (Exception err) {
                    script.currentBuild.result = 'FAILURE'
                    throw err
                } finally {
                    switch (script.currentBuild.result) {
                        case 'SUCCESS':
                            util.slack.notify(context, 'SUCCESS')
                            break
                        case 'UNSTABLE':
                            util.slack.notify(context, 'UNSTABLE')
                            break
                        // Case failure will be handled in stage
                        case 'FAILURE':
                            break
                    }

                    script.deleteDir()
                }
            }
        }
    }
}

