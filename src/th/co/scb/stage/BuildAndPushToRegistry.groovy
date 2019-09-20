package th.co.scb.stage

import th.co.scb.PipelineContext


class BuildAndPushToRegistry {

    def script
    String imgName
    PipelineContext context

    BuildAndPushToRegistry(script, PipelineContext context) {
        this.script = script
        this.context = context
    }


    def execute() {
        script.stage('Build and push to registry') {
            String currentResult = null

            try {
                if (context.ciSkip) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }

                // TODO: DECISION TO HANDLE IN INDIVIDUAL REPOSITORY JENKINSFILE ?
                if (!(context.gitBranchName =~ /(master|develop|release)/)) {
                    context.util.skipPipeline(script.env.STAGE_NAME)
                    currentResult = 'SKIPPED'
                } else {
                    context.util.info "Running ${script.env.STAGE_NAME}..."

                    script.withCredentials([script.usernamePassword(credentialsId: "${context.registryCredential}", usernameVariable: "USERNAME", passwordVariable: "PASSWORD")]) {

                        if ((context.gitBranchName =~ /(master|develop|release)/)) {
                            imgName = context.gitBranchName
                        }else{
                            imgName = 'feature'
                        }
                        Integer pipelineStatusCode = script.sh(
                            returnStatus: true, label: "build and push image to registry", script: """
                                docker login -u \${USERNAME} -p \${PASSWORD} ${context.registryUrl}
                                docker build -t ${context.containerName}:${imgName}-${context.tagVersion} .
                                docker push ${context.containerName}:${imgName}-${context.tagVersion}
                                docker tag ${context.containerName}:${imgName}-${context.tagVersion} ${context.containerName}:${imgName}
                                docker push ${context.containerName}:${imgName}
                            """
                        )

                        if (pipelineStatusCode != 0) {
                            currentResult = 'FAILURE'
                        } else {
                            currentResult = 'SUCCESS'
                        }
                    }
                }
            } catch (err) {
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
