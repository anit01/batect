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

package batect.ui

import batect.docker.ProcessOutput
import batect.docker.ProcessRunner
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ConsoleInfoSpec : Spek({
    describe("a console information provider") {
        describe("determining if STDIN is connected to a TTY") {
            on("STDIN being connected to a TTY") {
                val processRunner = mock<ProcessRunner>() {
                    on { runAndCaptureOutput(listOf("tty")) } doReturn ProcessOutput(0, "/dev/pts/0")
                }

                val consoleInfo = ConsoleInfo(processRunner)

                it("returns true") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(true))
                }
            }

            on("STDIN not being connected to a TTY") {
                val processRunner = mock<ProcessRunner>() {
                    on { runAndCaptureOutput(listOf("tty")) } doReturn ProcessOutput(1, "not a tty")
                }

                val consoleInfo = ConsoleInfo(processRunner)

                it("returns false") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(false))
                }
            }
        }
    }
})
