package th.co.scb.stage

import th.co.scb.PipelineContext

class ConfigDiffCheck {

    def script
    PipelineContext context

    ConfigDiffCheck(script, PipelineContext context) {
        this.script = script
        this.context = context
    }

    def execute() {          
        script.stage('Config Diff Check') {
            String currentResult = null

            try {
                if ( context.ciSkip ) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }

                if (!(context.gitBranchName =~ /(master|develop|release)/)) {
                    context.util.skipPipeline(script.env.STAGE_NAME)
                    currentResult = 'SKIPPED'
                } else {
                    if ( !context.previousStage.contains('Vault Diff Check') ) {
                        context.util.error "must run 'Vault Diff Check' stage before this stage"
                        currentResult = 'FAILURE'
                    } else {
                        context.util.info "Running ${script.env.STAGE_NAME}..."

                        def businessUnit = context.businessUnit
                        def envArray = new String[2]
                        envArray[0] = "staging"
                        envArray[1] = "production"

                        // ------------------- Create Config In Staging List -------------------
                        def fileConfigNonProd = script.sh (
                            returnStdout: true, script: """
                                echo \$(ls ${script.env.WORKSPACE}/app-configs/configuration/${envArray[0]})
                            """
                        ).trim()
                        def listFileConfigNonProd = []
                        listFileConfigNonProd = fileConfigNonProd.tokenize(' ')
                        def listConfigKeyNonProd = []
                        def configInPropertiesNonProd
                        def configInPropertiesKeyNonProd = []
                        def i
                        if ( listFileConfigNonProd.size() == 0 ) {
                            context.util.info "File config in ${envArray[0]} does not exist."
                        } else {
                            for ( fileConfig in listFileConfigNonProd ) {
                                if ( fileConfig == "app.properties" ) {
                                    configInPropertiesNonProd = script.readProperties(file: "${script.env.WORKSPACE}/app-configs/configuration/${envArray[0]}/${fileConfig}")
                                    configInPropertiesKeyNonProd = configInPropertiesNonProd.keySet()
                                    for ( keyInProperties in configInPropertiesKeyNonProd ) {
                                        listConfigKeyNonProd.add(keyInProperties)
                                    }
                                } else {
                                    listConfigKeyNonProd.add(fileConfig)
                                }
                            }
                            context.util.info "Count Config in ${envArray[0]} is ${listConfigKeyNonProd.size()}"
                            i = 0
                            for ( keyNonProd in listConfigKeyNonProd ) {
                                context.util.info "\t${i + 1}. ${keyNonProd}"
                                i++
                            }
                        }

                        // ------------------- Create Config In Production List -------------------
                        def fileConfigProd = script.sh (
                            returnStdout: true, script: """
                                echo \$(ls ${script.env.WORKSPACE}/app-configs/configuration/${envArray[1]})
                            """
                        ).trim()
                        def listFileConfigProd = []
                        listFileConfigProd = fileConfigProd.tokenize(' ')
                        def listConfigKeyProd = []
                        def configInPropertiesProd
                        def configInPropertiesKeyProd = []
                        if ( listFileConfigProd.size() == 0 ) {
                            context.util.info "File config in ${envArray[1]} does not exist."
                        } else {
                            for ( fileConfig in listFileConfigProd ) {
                                if ( fileConfig == "app.properties" ) {
                                    configInPropertiesProd = script.readProperties(file: "${script.env.WORKSPACE}/app-configs/configuration/${envArray[1]}/${fileConfig}")
                                    configInPropertiesKeyProd = configInPropertiesProd.keySet()
                                    for ( keyInProperties in configInPropertiesKeyProd ) {
                                        listConfigKeyProd.add(keyInProperties)
                                    }
                                } else {
                                    listConfigKeyProd.add(fileConfig)
                                }
                            }
                            context.util.info "Count Config in ${envArray[1]} is ${listConfigKeyProd.size()}"
                            i = 0
                            for ( keyProd in listConfigKeyProd ) {
                                context.util.info "\t${i + 1}. ${keyProd}"
                                i++
                            }
                        }
                        
                        // HANDLE CASE NO CONFIG
                        if ( listFileConfigNonProd.size() == 0 && listFileConfigProd.size() == 0 ) {
                            context.util.info "Don't have config file in every environment."
                            currentResult = 'FAILURE'
                        } else {
                            // ------------------- Compare config between non-production and prod -------------------
                            def intersect = listConfigKeyNonProd.intersect(listConfigKeyProd)
                            def union = listConfigKeyNonProd.plus(listConfigKeyProd)
                            def resultList = union - intersect

                            context.util.info "Count different Config is ${resultList.size()}"
                            if ( resultList.size() > 0 ) {
                                context.util.info "\t!!! Have Different. Please modify your config file.!!!"
                                def statusPrintTopic = false
                                def n = 0
                                for ( result in resultList ) {
                                    for ( keyNonprod in listConfigKeyNonProd ) {
                                        if ( !listConfigKeyNonProd.contains(result) ) {
                                            n++
                                            if ( !statusPrintTopic ) {
                                                context.util.info "\t Config on ${envArray[0]} environment does not have ..."
                                                statusPrintTopic = true
                                            }
                                            context.util.info "\t\t ${n}. ${result}"
                                            break;
                                        }
                                    }
                                }
                                statusPrintTopic = false
                                n = 0
                                for ( result in resultList ) {
                                    for ( keyProd in listConfigKeyProd ) {
                                        if ( !listConfigKeyProd.contains(result) ) {
                                            n++
                                            if ( !statusPrintTopic ) {
                                                context.util.info "\t Config on ${envArray[1]} environment does not have ..."
                                                statusPrintTopic = true
                                            }
                                            context.util.info "\t\t ${n}. ${result}"
                                            break;
                                        }
                                    }
                                }
                                currentResult = 'FAILURE'
                            } else {
                                currentResult = "SUCCESS"
                            }
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
