package th.co.scb

interface Context {
    boolean getDebug()

    def assemble()

    String getProjectName()

    String getJobName()

    String getBuildURL()

    String getBuildNumber()

    String getGitURL()

    String getPipelineType()

    String getRegistryCredential()

    String getConfigRepoURL()

    String getConfigRepoCredential()

    String getS3Bucket()

    String getSubgroup()

    String getJenkinsAgentNode()

    boolean getCiSkip()

    String getEnvironment()

    String getTagVersion()

    String getGitBranch()

    String getGitBranchName()

    String getContainerName()

    String getRegistryUrl()

    List getKubernetesContexts()

    List getPreviousStage()

    String addPreviousStage(def stage)
}

