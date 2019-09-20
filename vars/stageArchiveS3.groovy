#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.ArchiveS3


def call(PipelineContext context, kubernetesContext) {
    ArchiveS3 archiveS3 = new ArchiveS3(this, context, kubernetesContext)
    archiveS3.execute()
}

return this
