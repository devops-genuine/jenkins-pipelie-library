# Shared Pipeline Library

This repository contains a library of reusable Jenkins Pipeline steps

## Requirement Plugins

- [Blue Ocean Plugin](https://plugins.jenkins.io/blueocean)
- [Branch API Plugin](https://plugins.jenkins.io/branch-api)
- [Pipeline: AWS Steps](https://plugins.jenkins.io/pipeline-aws)
- [Gitlab](https://plugins.jenkins.io/gitlab-plugin)

## Prerequisite

- Slack channel
- Gitlab repository

## How to use library

Add this repository to `Configure System` -> `Global Pipeline Libraries`
then add the following to the top of your `Jenkinsfile`

```groovy
@Library('<library-name>@<branch>')
```

## Pipeline DSL parameters

In order to use this library you have to add `Jenkinsfile` with following

```groovy
@Library('<library-name>@<branch>') _

// Need for gitlabPlugin to work
properties([
    gitLabConnection('pipeline@gitlab')
])

stdPipeline([
    // This paramerter is set to deployment name
    // app name use in pipeline to push and pull from docker hub <eg. gitlab>
    // must reflect on gitlab url
    projectName: '<appName>',
    // default configurations using jenkins DSL
    // language avaliable golang,js
    pipelineType = '<language>',
    // Shared utility tools such as slack
    utility: [
            gitlabPlugin: true,
            slackPlugin: [
                    baseUrl: '<webhookURL>',
                    channel: '#<channelName>',
                    teamDomain: '<teamName>',
                    tokenCredentialId: '<webhookToken>',
            ]
    ],
    // Jenkins exexcutor configuration
    jenkinsConfig: [
            agent : 'slave'
    ],
    // Docker registry configuration
    registry: [
            url: 'registry.gitlab.com',
            group: '<groupName>',
            project: '<appName>',
            registryCredential: '<jenkinsCredentail>'
    ],
    // Amazon S3 bucknet name to store kubernetes manifests
    s3Bucket: '<bucketName>',
    // Mapping branch name with current environments to generate pipeline contexts
    branchToEnvironmentMapping: [
            'master': ['staging', 'production'],
            'develop': ['develop'],
            '*': ['review']
    ],
    // Kubernetes context, the values which are passed to pipeline to integrate with K8S
    kubernetes: [
            environmentToContextMapping: [
                    'production': '<contextName>,
                    'staging': '<contextName>,',
                    'develop': '<contextName>,',
            ],
            environmentToNamespaceMapping: [
                    'production': '<envName>',
                    'staging': '<envName>',
                    'develop': '<envName>',
            ],
            environmentToIngressClassMapping: [
                    'production': '<ingressClassName>',
                    'staging': '<ingressClassName>',
                    'develop': '<ingressClassName>',
            ],
            environmentToIngressHostnameMapping: [
                    'production': '<domainName>',
                    'staging': '<domainName>',
                    'develop': '<domainName>',
            ]
    ]
]) { context ->
  stageBuild(context)
  stageLint(context)
  stageUnitTest(context)
  stageBuildAndPushToRegistry(context)
  stageBuildManifest(context)
  stageDeploy(context)
  stageUploadManifest(context)
}

```
