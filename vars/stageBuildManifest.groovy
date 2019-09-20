#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.BuildManifest


def call(PipelineContext context) {
    BuildManifest buildManifest = new BuildManifest(this, context)
    buildManifest.execute()
}

return this