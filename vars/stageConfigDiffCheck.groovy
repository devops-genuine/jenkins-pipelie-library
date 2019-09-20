#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.ConfigDiffCheck


def call(PipelineContext context) {
    ConfigDiffCheck configDiffCheck = new ConfigDiffCheck(this, context)
    configDiffCheck.execute()
}

return this