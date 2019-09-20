package th.co.scb.stage

import th.co.scb.PipelineContext

class UploadManifest {

    def script
    PipelineContext context
    //Map kubernetesContext

    UploadManifest(script, PipelineContext context) {
        this.script = script
        this.context = context
        //this.kubernetesContext = kubernetesContext
        //this.file = "deployment-${context.projectName}-${context.gitBranchName}-${context.tagVersion}.yaml"
    }

    def execute() {
        script.stage('Upload K8S Manifest') {
            String currentResult = null
            String file

            try {
                if (context.ciSkip) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }

                if (!(context.gitBranchName =~ /(master|develop|release)/)) {
                    context.util.skipPipeline(script.env.STAGE_NAME)
                    currentResult = 'SKIPPED'
                } else {

                    if (!context.previousStage.contains('Deployment')) {
                        context.util.error "must run 'Deploy' stage before this stage"
                        currentResult = 'FAILURE'
                    } else {
                        
                        def deploymentFile
                        def fileDeploymentArtifact
                        def filePath
                        def fileConfigKeyExist

                        for (Map kubeContext in context.kubernetesContexts) {

                            deploymentFile = "deployment-${context.projectName}-${kubeContext['environment']}-${context.tagVersion}.yaml"
                            fileDeploymentArtifact = "${context.projectName}-${context.tagVersion}.tar.gz"
                            filePath = "${script.env.WORKSPACE}/pipeline-app-configs/${kubeContext['environment']}/${context.projectName}/configuration/app.properties"
                            fileConfigKeyExist = script.fileExists(filePath)
                            script.sh(
                                returnStatus: true, label: "Compress artifact", script: """
                                    mkdir -p \$(pwd)/${kubeContext['environment']}/${context.projectName}-${context.tagVersion}
                                    cp -Rf ${script.env.WORKSPACE}/pipeline-app-configs/${kubeContext['environment']}/${context.projectName}/configuration/* \$(pwd)/${kubeContext['environment']}/${context.projectName}-${context.tagVersion}
                                    cp -Rf ${script.env.WORKSPACE}/pipeline-app-configs/${kubeContext['environment']}/${context.projectName}/secret/* \$(pwd)/${kubeContext['environment']}/${context.projectName}-${context.tagVersion}
                                    cd \$(pwd)/${kubeContext['environment']} && tar -zcvf ${fileDeploymentArtifact} ${context.projectName}-${context.tagVersion}
                                """
                            )

                            script.s3Upload(
                                file: "${kubeContext['environment']}/${fileDeploymentArtifact}",
                                bucket: context.s3Bucket,
                                path: "${context.projectName}/${kubeContext['environment']}/configurations/${fileDeploymentArtifact}",
                            )

                            script.s3Upload(
                                file: deploymentFile,
                                bucket: context.s3Bucket,
                                path: "${context.projectName}/${kubeContext['environment']}/k8s-manifest/${deploymentFile}",
                            )

                        }
                        currentResult = 'SUCCESS'
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

