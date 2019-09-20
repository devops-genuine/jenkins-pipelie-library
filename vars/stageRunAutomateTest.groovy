#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.RunAutomateTest

def call(PipelineContext context) {
    RunAutomateTest runAutomateTest = new RunAutomateTest(this, context, context.pipelineType)
    runAutomateTest.execute()
}

return this