/*
   Copyright 2017-2019 Charles Korn.

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

package batect.config.io

import batect.config.BuildImage
import batect.config.HealthCheckConfig
import batect.config.LiteralValue
import batect.config.PortMapping
import batect.config.PullImage
import batect.config.RunAsCurrentUserConfig
import batect.config.VolumeMount
import batect.config.io.deserializers.PathDeserializer
import batect.os.Command
import batect.os.PathResolutionResult
import batect.os.PathType
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import kotlinx.serialization.ElementValueDecoder
import kotlinx.serialization.context.SimpleModule
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Paths
import java.time.Duration

object ContainerFromFileSpec : Spek({
    describe("a container read from a configuration file") {
        val pathDeserializer by createForEachTest {
            mock<PathDeserializer> {
                on { deserialize(any()) } doAnswer { invocation ->
                    val input = invocation.arguments[0] as ElementValueDecoder
                    val path = input.decodeString()

                    when (path) {
                        "/does_not_exist" -> PathResolutionResult.Resolved(path, Paths.get("/some_resolved_path"), PathType.DoesNotExist)
                        "/file" -> PathResolutionResult.Resolved(path, Paths.get("/some_resolved_path"), PathType.File)
                        "/not_file_or_directory" -> PathResolutionResult.Resolved(path, Paths.get("/some_resolved_path"), PathType.Other)
                        "/invalid" -> PathResolutionResult.InvalidPath(path)
                        else -> PathResolutionResult.Resolved(path, Paths.get("/resolved" + path), PathType.Directory)
                    }
                }
            }
        }

        val parser by createForEachTest { Yaml() }
        beforeEachTest { parser.install(SimpleModule(PathResolutionResult::class, pathDeserializer)) }

        given("the config file has just a build directory") {
            given("and that directory exists") {
                val yaml = "build_directory: /some_build_dir"

                on("loading the configuration from the config file") {
                    val result = parser.parse(ContainerFromFile.Companion, yaml)

                    it("returns the expected container configuration, with the build directory resolved to an absolute path") {
                        assertThat(result, equalTo(ContainerFromFile(BuildImage("/resolved/some_build_dir"))))
                    }
                }
            }

            data class BuildDirectoryResolutionTestCase(val description: String, val originalPath: String, val expectedMessage: String)

            setOf(
                BuildDirectoryResolutionTestCase(
                    "does not exist",
                    "/does_not_exist",
                    "Build directory '/does_not_exist' (resolved to '/some_resolved_path') does not exist."
                ),
                BuildDirectoryResolutionTestCase(
                    "is a file",
                    "/file",
                    "Build directory '/file' (resolved to '/some_resolved_path') is not a directory."
                ),
                BuildDirectoryResolutionTestCase(
                    "is neither a file or directory",
                    "/not_file_or_directory",
                    "Build directory '/not_file_or_directory' (resolved to '/some_resolved_path') is not a directory."
                ),
                BuildDirectoryResolutionTestCase(
                    "is an invalid path",
                    "/invalid",
                    "Build directory '/invalid' is not a valid path."
                )
            ).forEach { (description, originalPath, expectedMessage) ->
                given("and that path $description") {
                    val yaml = "build_directory: $originalPath"

                    on("loading the configuration from the config file") {
                        it("throws an appropriate exception") {
                            assertThat(
                                { parser.parse(ContainerFromFile.Companion, yaml) },
                                throws(withMessage(expectedMessage) and withLineNumber(1) and withColumn(1))
                            )
                        }
                    }
                }
            }
        }

        given("the config file has just an image") {
            val yaml = "image: some_image"

            on("loading the configuration from the config file") {
                val result = parser.parse(ContainerFromFile.Companion, yaml)

                it("returns the expected container configuration") {
                    assertThat(result, equalTo(ContainerFromFile(PullImage("some_image"))))
                }
            }
        }

        given("the config file has both a build directory and an image") {
            val yaml = """
                image: some_image
                build_directory: /build_dir
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.parse(ContainerFromFile.Companion, yaml) },
                        throws(withMessage("Only one of build_directory or image can be specified for a container, but both have been provided for this container.") and withLineNumber(1) and withColumn(1))
                    )
                }
            }
        }

        given("the config file has neither a build directory nor an image") {
            val yaml = """
                command: do-the-thing
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.parse(ContainerFromFile.Companion, yaml) },
                        throws(withMessage("One of either build_directory or image must be specified for each container, but neither have been provided for this container.") and withLineNumber(1) and withColumn(1))
                    )
                }
            }
        }

        given("the config file has all optional fields specified") {
            val yaml = """
                build_directory: /container-1-build-dir
                command: do-the-thing.sh some-param
                environment:
                  OPTS: -Dthing
                  INT_VALUE: 1
                  FLOAT_VALUE: 12.6000
                  BOOL_VALUE: true
                  OTHER_VALUE: "the value"
                working_directory: /here
                volumes:
                  - /volume1:/here
                  - /somewhere:/else:ro
                ports:
                  - "1234:5678"
                  - "9012:3456"
                health_check:
                  interval: 2s
                  retries: 10
                  start_period: 1s
                run_as_current_user:
                  enabled: true
                  home_directory: /home/something
            """.trimIndent()

            on("loading the configuration from the config file") {
                val result = parser.parse(ContainerFromFile.Companion, yaml)

                it("returns the expected container configuration") {
                    assertThat(result.imageSource, equalTo(BuildImage("/resolved/container-1-build-dir")))
                    assertThat(result.command, equalTo(Command.parse("do-the-thing.sh some-param")))
                    assertThat(
                        result.environment, equalTo(
                            mapOf(
                                "OPTS" to LiteralValue("-Dthing"),
                                "INT_VALUE" to LiteralValue("1"),
                                "FLOAT_VALUE" to LiteralValue("12.6000"),
                                "BOOL_VALUE" to LiteralValue("true"),
                                "OTHER_VALUE" to LiteralValue("the value")
                            )
                        )
                    )
                    assertThat(result.workingDirectory, equalTo("/here"))
                    assertThat(
                        result.volumeMounts, equalTo(
                            setOf(
                                VolumeMount("/resolved/volume1", "/here", null),
                                VolumeMount("/resolved/somewhere", "/else", "ro")
                            )
                        )
                    )
                    assertThat(result.portMappings, equalTo(setOf(PortMapping(1234, 5678), PortMapping(9012, 3456))))
                    assertThat(result.healthCheckConfig, equalTo(HealthCheckConfig(Duration.ofSeconds(2), 10, Duration.ofSeconds(1))))
                    assertThat(result.runAsCurrentUserConfig, equalTo(RunAsCurrentUserConfig.RunAsCurrentUser("/home/something")))
                }
            }
        }
    }
})
