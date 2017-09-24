/*
   Copyright 2017 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.model.events

import batect.config.BuildImage
import batect.config.Container
import batect.docker.DockerImageBuildProgress
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ImageBuildProgressEventSpec : Spek({
    describe("an 'image build progress' event") {
        val container = Container("container-1", BuildImage("/container-1-build-dir"))
        val event = ImageBuildProgressEvent(container, DockerImageBuildProgress(1, 10, "Something is happening"))

        on("being applied") {
            val context = mock<TaskEventContext>()
            event.apply(context)

            it("does not do anything") {
                verifyZeroInteractions(context)
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                com.natpryce.hamkrest.assertion.assertThat(event.toString(), equalTo("ImageBuildProgressEvent(container: 'container-1', current step: 1, total steps: 10, message: 'Something is happening')"))
            }
        }
    }
})