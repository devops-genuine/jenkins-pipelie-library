#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.UploadManifest


def call(PipelineContext context) {
    UploadManifest uploadManifest = new UploadManifest(this, context)
    uploadManifest.execute()
}

return this