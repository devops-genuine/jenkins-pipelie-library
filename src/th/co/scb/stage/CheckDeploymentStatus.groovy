package th.co.scb.stage

import th.co.scb.PipelineContext

class CheckDeploymentStatus {

    def script
    PipelineContext context
    String deployToK8SContext
    String targetNamespace

    CheckDeploymentStatus(script, PipelineContext context) {
        this.script = script
        this.context = context
    }

    def execute() {
        script.stage('Check Deployment Status') {
            String currentResult = null   
            Integer pipelineStatusCode         
            
            try {
                if (!(context.gitBranchName =~ /(master|develop|release)/)) {
                    context.util.skipPipeline(script.env.STAGE_NAME)
                    currentResult = 'SKIPPED'
                } else {
                    if ( !context.previousStage.contains('Deployment') ) {
                        context.util.error "must run 'Deployment' stage before this stage"
                        currentResult = 'FAILURE'
                    } else {
                        for (Map kubeContext in context.kubernetesContexts) {
                            if(kubeContext['environment'] != "production"){
                                deployToK8SContext = kubeContext['kubernetesContext']
                                targetNamespace = kubeContext['kubernetesNamespace']
                            }
                        }

                    // ----------------- Check Deployment Status -----------------
                        script.timeout(time: 5, unit: 'MINUTES') {
                            pipelineStatusCode = script.sh(
                                returnStatus: true, label: "Check deployment status in kubernetes", script: """
                                    kubectl config use-context ${deployToK8SContext}

                                    echo "Check deployment ${context.projectName} status in Cluster"
                                    kubectl rollout status deployment ${context.projectName} -n ${targetNamespace}
                                """
                            )
                            println("hello status code is ${pipelineStatusCode}")
                        }
                    }
                }
            } catch (Exception err) {
                currentResult = 'FAILURE'
                throw err
            } finally {
                switch (currentResult) {
                    case 'SKIPPED':
                        break
                    case 'SUCCESS':
                        context.util.gitlabCommitStatus.success(script.env.STAGE_NAME)
                        script.currentBuild.result = currentResult
                        // archiveArtifact()
                        break
                    case 'UNSTABLE':
                        context.util.gitlabCommitStatus.failed(script.env.STAGE_NAME)
                        script.currentBuild.result = currentResult
                        script.error('Unstable')
                        break
                    case 'FAILURE':
                        context.util.gitlabCommitStatus.failed(script.env.STAGE_NAME)
                        script.currentBuild.result = currentResult
                        context.util.slack.notify(context, currentResult, script.env.STAGE_NAME)
                        script.error('Failed')
                        break
                }

                context.addPreviousStage(script.env.STAGE_NAME)
            }
        }
    }
}