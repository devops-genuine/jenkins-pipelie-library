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

                if (!(context.gitBranchName =~ /(master|release)/)) {
                    context.util.skipPipeline(script.env.STAGE_NAME)
                    currentResult = 'SKIPPED'
                } else {
                    if ( !context.previousStage.contains('Vault Diff Check') ) {
                        context.util.error "Must run 'Vault Diff Check' stage before this stage"
                        currentResult = 'FAILURE'
                    } else {

                        context.util.info "Running ${script.env.STAGE_NAME}..."

                        def businessUnit = context.businessUnit
                        def envArray = new String[3]
                        envArray[0] = "develop"
                        envArray[1] = "test"
                        envArray[2] = "staging"

                        def fileConfigKeyExistDevelop = script.fileExists("${script.env.WORKSPACE}/pipeline-app-configs/${envArray[0]}/${context.projectName}/configuration/app.properties")
                        def fileConfigKeyExistTest = script.fileExists("${script.env.WORKSPACE}/pipeline-app-configs/${envArray[1]}/${context.projectName}/configuration/app.properties")
                        def fileConfigKeyExistStaging = script.fileExists("${script.env.WORKSPACE}/pipeline-app-configs/${envArray[2]}/${context.projectName}/configuration/app.properties")

                        // ------------------------- Declare Variables -------------------------

                        def configDevelopContent
                        def configKeyDevelop

                        def configTestContent
                        def configKeyTest

                        def configStagingContent
                        def configKeyStaging

                        def i

                        if ( context.gitBranchName =~ /(release)/ ) {
                        
                            context.util.info "Checking Configuration Properties Diff Between develop and test environments"

                            // ------------------------- Get Config in develop -------------------------
                            if ( fileConfigKeyExistDevelop == false ) {
                                context.util.info "app.properties in ${envArray[0]} does not exist."
                            } else {
                                configDevelopContent = script.readProperties(file: "${script.env.WORKSPACE}/pipeline-app-configs/${envArray[0]}/${context.projectName}/configuration/app.properties");
                                configKeyDevelop = configDevelopContent.keySet()
                                context.util.info "Count Config key in ${envArray[0]} in app.properties is ${configKeyDevelop.size()}"
                                i = 0
                                for ( configKey in configKeyDevelop ) {
                                    context.util.info "\t${i + 1}. ${configKey}"
                                    i++
                                }
                            }
                            // ------------------------------------------------------------------------

                            // ------------------------- Get Config in test -------------------------
                            if ( fileConfigKeyExistTest == false ) {
                                context.util.info "app.properties in ${envArray[1]} does not exist."
                            } else {
                                configTestContent = script.readProperties(file: "${script.env.WORKSPACE}/pipeline-app-configs/${envArray[1]}/${context.projectName}/configuration/app.properties");
                                configKeyTest = configTestContent.keySet()
                                context.util.info "Count Config key in ${envArray[1]} in app.properties is ${configKeyTest.size()}"
                                i = 0
                                for ( configKey in configKeyTest ) {
                                    context.util.info "\t${i + 1}. ${configKey}"
                                    i++
                                }
                            }
                            // ------------------------------------------------------------------------

                            def fileConfigExistArray = new Boolean[2]
                            fileConfigExistArray[0] = fileConfigKeyExistDevelop
                            fileConfigExistArray[1] = fileConfigKeyExistTest
                            def fileConfigKeyExist = true
                            for ( statusFileExist in fileConfigExistArray ) {
                                if ( statusFileExist == false ) {
                                    fileConfigKeyExist = false
                                    break;
                                }
                            }

                            if ( ( fileConfigExistArray[0] && fileConfigExistArray[1] ) == false) {
                                // IGNORE IF DON'T HAVE VAULT-KEY.JSON IN EVERY ENVIRONMENT.
                                context.util.info "Don't have app.properties file in every environment."
                                currentResult = 'SUCCESS'
                            } else if ( fileConfigKeyExist == false ) {
                                currentResult = 'FAILURE'
                            } else if ( fileConfigKeyExist == true ) {

                                def intersect = configKeyDevelop.intersect(configKeyTest)
                                def union = configKeyDevelop.plus(configKeyTest)
                                def resultList = union - intersect

                                context.util.info "Count different config key is ${resultList.size()}"

                                if ( resultList.size() > 0 ) {

                                    context.util.info "\t!!! Have Different. Please add missing configuartion properties!!!"
                                    
                                    // ------------------------- Prinln result in develop -------------------------
                                    def statusPrintTopic = false
                                    def n = 0
                                    for ( result in resultList ) {
                                        for ( configKey in configKeyDevelop ) {
                                            if ( !configKeyDevelop.contains(result) ) {
                                                n++
                                                if ( !statusPrintTopic ) {
                                                    context.util.info "\t Key properties on ${envArray[0]} environment does not have ..."
                                                    statusPrintTopic = true
                                                }
                                                context.util.info "\t\t ${n}. ${result}"
                                                break;
                                            }
                                        }
                                    }
                                    // ------------------------- Prinln result in test ------------------------
                                    statusPrintTopic = false
                                    n = 0
                                    for ( result in resultList ) {
                                        for ( configKey in configKeyTest ) {
                                            if ( !configKeyTest.contains(result) ) {
                                                n++
                                                if ( !statusPrintTopic ) {
                                                    context.util.info "\t Key properties ${envArray[1]} environment does not have ..."
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

                        } // End context.gitBranchName ( Release )

                        // ===========================================================================

                        if ( context.gitBranchName =~ /(master)/ ) {
                        
                            context.util.info "Checking Configuration Properties Diff Between test and staging environments"

                            // ------------------------- Get Config in test -------------------------
                            if ( fileConfigKeyExistTest == false ) {
                                context.util.info "app.properties in ${envArray[1]} does not exist."
                            } else {
                                configTestContent = script.readProperties(file: "${script.env.WORKSPACE}/pipeline-app-configs/${envArray[1]}/${context.projectName}/configuration/app.properties");
                                configKeyTest = configTestContent.keySet()
                                context.util.info "Count Config key in ${envArray[1]} in app.properties is ${configKeyTest.size()}"
                                i = 0
                                for ( configKey in configKeyTest ) {
                                    context.util.info "\t${i + 1}. ${configKey}"
                                    i++
                                }
                            }
                            // ------------------------------------------------------------------------

                            // ------------------------- Get Config in staging -------------------------
                            if ( fileConfigKeyExistStaging == false ) {
                                context.util.info "app.properties in ${envArray[2]} does not exist."
                            } else {
                                configStagingContent = script.readProperties(file: "${script.env.WORKSPACE}/pipeline-app-configs/${envArray[2]}/${context.projectName}/configuration/app.properties");
                                configKeyStaging = configStagingContent.keySet()
                                context.util.info "Count Config key in ${envArray[2]} in app.properties is ${configKeyStaging.size()}"
                                i = 0
                                for ( configKey in configKeyStaging ) {
                                    context.util.info "\t${i + 1}. ${configKey}"
                                    i++
                                }
                            }
                            // ------------------------------------------------------------------------

                            def fileConfigExistArray = new Boolean[2]
                            fileConfigExistArray[0] = fileConfigKeyExistTest
                            fileConfigExistArray[1] = fileConfigKeyExistStaging
                            def fileConfigKeyExist = true
                            for ( statusFileExist in fileConfigExistArray ) {
                                if ( statusFileExist == false ) {
                                    fileConfigKeyExist = false
                                    break;
                                }
                            }

                            if ( ( fileConfigExistArray[0] && fileConfigExistArray[1] ) == false) {
                                // IGNORE IF DON'T HAVE VAULT-KEY.JSON IN EVERY ENVIRONMENT.
                                context.util.info "Don't have app.properties file in every environment."
                                currentResult = 'SUCCESS'
                            } else if ( fileConfigKeyExist == false ) {
                                currentResult = 'FAILURE'
                            } else if ( fileConfigKeyExist == true ) {

                                def intersect = configKeyTest.intersect(configKeyStaging)
                                def union = configKeyTest.plus(configKeyStaging)
                                def resultList = union - intersect

                                context.util.info "Count different config key is ${resultList.size()}"

                                if ( resultList.size() > 0 ) {

                                    context.util.info "\t!!! Have Different. Please add missing configuartion properties!!!"
                                    
                                    // ------------------------- Prinln result in develop -------------------------
                                    def statusPrintTopic = false
                                    def n = 0
                                    for ( result in resultList ) {
                                        for ( configKey in configKeyTest ) {
                                            if ( !configKeyTest.contains(result) ) {
                                                n++
                                                if ( !statusPrintTopic ) {
                                                    context.util.info "\t Key properties ${envArray[1]} environment does not have ..."
                                                    statusPrintTopic = true
                                                }
                                                context.util.info "\t\t ${n}. ${result}"
                                                break;
                                            }
                                        }
                                    }
                                    // ------------------------- Prinln result in test ------------------------
                                    statusPrintTopic = false
                                    n = 0
                                    for ( result in resultList ) {
                                        for ( configKey in configKeyStaging ) {
                                            if ( !configKeyStaging.contains(result) ) {
                                                n++
                                                if ( !statusPrintTopic ) {
                                                    context.util.info "\t Key properties ${envArray[2]} environment does not have ..."
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

                        } // End context.gitBranchName ( Master )
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