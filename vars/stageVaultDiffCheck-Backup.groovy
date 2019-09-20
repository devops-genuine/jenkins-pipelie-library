#!/usr/bin/env groovy

import th.co.scb.PipelineContext
// import th.co.scb.stage.VaultDiffCheck
import groovy.json.JsonSlurperClassic

def call(PipelineContext context) {
    // VaultDiffCheck vaultDiffCheck = new VaultDiffCheck(this, context, context.pipelineType)
    // vaultDiffCheck.execute()
    stage('Vault Diff Check') {
        String currentResult = null

        try {
            if (context.ciSkip) {
                context.util.info "${env.STAGE_NAME} stage skipped due to git commit override"
                currentResult = 'SKIPPED'
            }
            
            if (!(context.gitBranchName =~ /(master|develop|release)/)) {
                context.util.skipPipeline(env.STAGE_NAME)
                currentResult = 'SKIPPED'
            } else {
                context.util.info "Running ${env.STAGE_NAME}..."

                def envArray = new String[3]
                def businessUnit = context.businessUnit
                envArray[0] = "develop"
                envArray[1] = "test"
                envArray[2] = "staging"

                def fileVaultKeyExistDevelop = fileExists("${WORKSPACE}/app-configs/secret/${envArray[0]}/vault-key.json")
                def fileVaultKeyExistTest = fileExists("${WORKSPACE}/app-configs/secret/${envArray[1]}/vault-key.json")
                def fileVaultKeyExistStaging = fileExists("${WORKSPACE}/app-configs/secret/${envArray[2]}/vault-key.json")

                // ------------------------- Get vault-key in develop -------------------------
                def vaultJsonKeyDevelop
                def vaultJsonKeyDevelopContent
                def vaultKeyDevelop
                def i = 0
                if ( fileVaultKeyExistDevelop == false ) {
                    context.util.info "vault-key.json in ${envArray[0]} does not exist."
                } else {
                    vaultJsonKeyDevelop = readFile("${WORKSPACE}/app-configs/secret/${envArray[0]}/vault-key.json");
                    vaultJsonKeyDevelopContent = new JsonSlurperClassic().parseText(vaultJsonKeyDevelop)
                    vaultKeyDevelop = vaultJsonKeyDevelopContent.keySet()
                    context.util.info "Count Vault key in ${envArray[0]} in vault-key.json is ${vaultKeyDevelop.size()}"
                    i = 0
                    for ( vaultKey in vaultKeyDevelop ) {
                        context.util.info "\t${i + 1}. ${vaultKey}"
                        i++
                    }
                }

                // ------------------------- Get vault-key in test -------------------------
                def vaultJsonKeyTest
                def vaultJsonKeyTestContent
                def vaultKeyTest
                if ( fileVaultKeyExistTest == false ) {
                    context.util.info "vault-key.json in ${envArray[1]} does not exist."
                } else {
                    vaultJsonKeyTest = readFile("${WORKSPACE}/app-configs/secret/${envArray[1]}/vault-key.json");
                    vaultJsonKeyTestContent = new JsonSlurperClassic().parseText(vaultJsonKeyTest)
                    vaultKeyTest = vaultJsonKeyTestContent.keySet()
                    context.util.info "Count Vault key in ${envArray[1]} in vault-key.json is ${vaultKeyTest.size()}"
                    i = 0
                    for ( vaultKey in vaultKeyTest ) {
                        context.util.info "\t${i + 1}. ${vaultKey}"
                        i++
                    }
                }

                // ------------------------- Get vault-key in staging -------------------------
                def vaultJsonKeyStaging
                def vaultJsonKeyStagingContent
                def vaultKeyStaging
                if ( fileVaultKeyExistStaging == false ) {
                    context.util.info "vault-key.json in ${envArray[2]} does not exist."
                } else {
                    vaultJsonKeyStaging = readFile("${WORKSPACE}/app-configs/secret/${envArray[2]}/vault-key.json");
                    vaultJsonKeyStagingContent = new JsonSlurperClassic().parseText(vaultJsonKeyStaging)
                    vaultKeyStaging = vaultJsonKeyStagingContent.keySet()
                    context.util.info "Count Vault key in ${envArray[2]} in vault-key.json is ${vaultKeyStaging.size()}"
                    i = 0
                    for ( vaultKey in vaultKeyStaging ) {
                        context.util.info "\t${i + 1}. ${vaultKey}"
                        i++
                    }
                }
                
                def fileVaultKeyExistArray = new Boolean[3]
                fileVaultKeyExistArray[0] = fileVaultKeyExistDevelop
                fileVaultKeyExistArray[1] = fileVaultKeyExistTest
                fileVaultKeyExistArray[2] = fileVaultKeyExistStaging
                def fileVaultKeyExist = true
                for ( statusFileExist in fileVaultKeyExistArray ) {
                    if ( statusFileExist == false ) {
                        fileVaultKeyExist = false
                        break;
                    }
                }
                
                if ( ( fileVaultKeyExistArray[0] && fileVaultKeyExistArray[1] && fileVaultKeyExistArray[2] ) == false) {
                    // IGNORE IF DON'T HAVE VAULT-KEY.JSON IN EVERY ENVIRONMENT.
                    context.util.info "Don't have vault-key.json file in every environment."
                    currentResult = 'SUCCESS'
                } else if ( fileVaultKeyExist == false ) {
                    currentResult = 'FAILURE'
                } else if ( fileVaultKeyExist == true ) {
                    def intersect = vaultKeyDevelop.intersect(vaultKeyTest.intersect(vaultKeyStaging))
                    def union = vaultKeyDevelop.plus(vaultKeyTest.plus(vaultKeyStaging))
                    def resultList = union - intersect

                    context.util.info "Count different Vault key is ${resultList.size()}"
                    if ( resultList.size() > 0 ) {
                        context.util.info "\t!!! Have Different. Please contact DevOps team to modify vault.!!!"
                        // ------------------------- Prinln result in develop -------------------------
                        def statusPrintTopic = false
                        def n = 0
                        for ( result in resultList ) {
                            for ( vaultKey in vaultKeyDevelop ) {
                                if ( !vaultKeyDevelop.contains(result) ) {
                                    n++
                                    if ( !statusPrintTopic ) {
                                        context.util.info "\t Vault key on ${envArray[0]} environment does not have ..."
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
                            for ( vaultKey in vaultKeyTest ) {
                                if ( !vaultKeyTest.contains(result) ) {
                                    n++
                                    if ( !statusPrintTopic ) {
                                        context.util.info "\t Vault key on ${envArray[1]} environment does not have ..."
                                        statusPrintTopic = true
                                    }
                                    context.util.info "\t\t ${n}. ${result}"
                                    break;
                                }
                            }
                        }
                        // ------------------------- Prinln result in staging ------------------------
                        statusPrintTopic = false
                        n = 0
                        for ( result in resultList ) {
                            for ( vaultKey in vaultKeyStaging ) {
                                if ( !vaultKeyStaging.contains(result) ) {
                                    n++
                                    if ( !statusPrintTopic ) {
                                        context.util.info "\t Vault key on ${envArray[2]} environment does not have ..."
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

        } catch (err) {
            currentResult = 'FAILURE'
            throw err
        } finally {
            switch (currentResult) {
                case 'SKIPPED':
                    break
                case 'SUCCESS':
                    context.util.gitlabCommitStatus.success(env.STAGE_NAME)
                    currentBuild.result = currentResult
                    break
                case 'UNSTABLE':
                    context.util.gitlabCommitStatus.failed(env.STAGE_NAME)
                    currentBuild.result = currentResult
                    error('Unstable')
                    break
                case 'FAILURE':
                    context.util.gitlabCommitStatus.failed(env.STAGE_NAME)
                    currentBuild.result = currentResult
                    context.util.slack.notify(context, currentResult, env.STAGE_NAME)
                    error('Failed')
                    break
            }
            context.addPreviousStage(env.STAGE_NAME)
        }
    }
}

return this