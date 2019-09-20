package th.co.scb.stage

import th.co.scb.PipelineContext

class BuildManifest {

    def script
    PipelineContext context
    String file
    
    //Map kubernetesContext

    BuildManifest(script, PipelineContext context) {
        this.script = script
        this.context = context
        //this.kubernetesContext = kubernetesContext
        //this.file = "deployment-${context.projectName}-${context.gitBranchName}-${context.tagVersion}.yaml"
    }

    def execute() {
        script.stage('Build K8S Manifests') {
            String currentResult = null
            String imgName
            Integer pipelineStatusCode

            try {
                if (context.ciSkip) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }

                if (!(context.gitBranchName =~ /(master|develop|release)/)) {
                    context.util.skipPipeline(script.env.STAGE_NAME)
                    currentResult = 'SKIPPED'
                } else {

                    if (!context.previousStage.contains('Build and push to registry')) {
                        context.util.error "must run 'Build and push to registry' stage before this stage"
                        currentResult = 'FAILURE'
                    } else {

                        for (Map kubeContext in context.kubernetesContexts) {

                            this.file = "deployment-${context.projectName}-${kubeContext['environment']}-${context.tagVersion}.yaml"

                            context.util.info "Building kubernetes manifest for environment : ${kubeContext['environment']}..."

                            if ((context.gitBranchName =~ /(master|develop|release)/)) {
                                imgName = context.gitBranchName
                            }else{
                                imgName = 'feature'
                            }

                            pipelineStatusCode = script.sh(
                                returnStatus: true, label: "build manifest for kubernetes", script: """
                                    echo 'Create deployment'
                                    cp deployment.yaml ${file}
                                    sed -i s/#APPNAME#/${context.projectName}/g ${file}
                                    sed -i s/#SUBGROUP#/${context.subgroup}/g ${file}
                                    sed -i s/#REGISTRY#/${context.projectName}:${imgName}-${context.tagVersion}/g ${file}
                                    sed -i s/#VERSION#/${context.tagVersion}/g ${file}
                                    sed -i s/#REPLICA#/${kubeContext['kubernetesPodReplicas']}/g ${file}
                                    sed -i s/#NAMESPACE#/${kubeContext['kubernetesNamespace']}/g ${file}
                                    sed -i s/#REPLICA#/${kubeContext['kubernetesPodReplicas']}/g ${file}
                                    sed -i s/#INGRESS#/${kubeContext['kubernetesIngressClass']}/g ${file}
                                    sed -i s/#HOSTNAME#/${kubeContext['kubernetesIngressHostname']}/g ${file}
                                """
                            )
                        }

                        if (pipelineStatusCode != 0) {
                            currentResult = 'FAILURE'
                            context.util.error "${script.env.STAGE_NAME} "
                        } else {
                            currentResult = 'SUCCESS'
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
                        //archiveArtifact()
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
