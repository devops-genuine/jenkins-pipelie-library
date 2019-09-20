#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.UnitTest

def call(PipelineContext context) {
    UnitTest unitTest = new UnitTest(this, context, context.pipelineType)
    unitTest.execute()
}

return this
