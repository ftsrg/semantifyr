/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.guice.common

import com.google.inject.AbstractModule
import com.google.inject.ConfigurationException
import com.google.inject.Guice
import com.google.inject.OutOfScopeException
import com.google.inject.ProvisionException
import com.google.inject.ScopeAnnotation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ScopeAnnotation
private annotation class TestScoped

private val testContext = ScopeContext("TestScope")

@TestScoped
class TestScopedService

private class ScopeBindingModule : AbstractModule() {
    override fun configure() {
        bindScope(TestScoped::class.java, testContext.scope)
    }
}

class ScopeContextTest {
    @Test
    fun `scoped class resolves outside an active scope throws OutOfScopeException wrapped in ProvisionException`() {
        val injector = Guice.createInjector(ScopeBindingModule())
        assertThatThrownBy {
            injector.getInstance(TestScopedService::class.java)
        }.isInstanceOf(ProvisionException::class.java).hasRootCauseInstanceOf(OutOfScopeException::class.java)
    }

    @Test
    fun `ConfigurationException is raised when parent lacks the scope binding`() {
        val parent = Guice.createInjector()
        parent.createChildInjector(ScopeBindingModule())

        assertThatThrownBy {
            parent.getInstance(TestScopedService::class.java)
        }.isInstanceOf(ConfigurationException::class.java)
    }

    @Test
    suspend fun `withScope - within one activation successive resolves return the same instance`() {
        val injector = Guice.createInjector(ScopeBindingModule())
        testContext.withScope {
            val a = injector.getInstance(TestScopedService::class.java)
            val b = injector.getInstance(TestScopedService::class.java)
            assertThat(a).isSameAs(b)
        }
    }

    @Test
    suspend fun `withScope - separate activations produce distinct instances`() {
        val injector = Guice.createInjector(ScopeBindingModule())
        val first = testContext.withScope {
            injector.getInstance(TestScopedService::class.java)
        }
        val second = testContext.withScope {
            injector.getInstance(TestScopedService::class.java)
        }
        assertThat(first).isNotSameAs(second)
    }

    @Test
    fun `withScope - parallel coroutines each in their own activation keep stores disjoint`() = runTest {
        val injector = Guice.createInjector(ScopeBindingModule())

        coroutineScope {
            val firstJob = async {
                testContext.withScope {
                    val a = injector.getInstance(TestScopedService::class.java)
                    yield()
                    val b = injector.getInstance(TestScopedService::class.java)
                    assertThat(a).isSameAs(b)
                    a
                }
            }
            val secondJob = async {
                testContext.withScope {
                    val a = injector.getInstance(TestScopedService::class.java)
                    yield()
                    val b = injector.getInstance(TestScopedService::class.java)
                    assertThat(a).isSameAs(b)
                    a
                }
            }
            val first = firstJob.await()
            val second = secondJob.await()
            assertThat(first).isNotSameAs(second)
        }
    }

    @Test
    suspend fun `withScope - re-entering on the same thread is rejected`() {
        testContext.withScope {
            assertThatThrownBy {
                runBlocking {
                    testContext.withScope {
                        // no-op
                    }
                }
            }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("already open")
        }
    }

    @Test
    fun `withScopeBlocking - within one activation successive resolves return the same instance`() {
        val injector = Guice.createInjector(ScopeBindingModule())
        testContext.withScopeBlocking {
            val a = injector.getInstance(TestScopedService::class.java)
            val b = injector.getInstance(TestScopedService::class.java)
            assertThat(a).isSameAs(b)
        }
    }

    @Test
    fun `withScopeBlocking - separate activations produce distinct instances`() {
        val injector = Guice.createInjector(ScopeBindingModule())
        val first = testContext.withScopeBlocking {
            injector.getInstance(TestScopedService::class.java)
        }
        val second = testContext.withScopeBlocking {
            injector.getInstance(TestScopedService::class.java)
        }
        assertThat(first).isNotSameAs(second)
    }

    @Test
    fun `withScopeBlocking - re-entering on the same thread is rejected`() {
        testContext.withScopeBlocking {
            assertThatThrownBy {
                testContext.withScopeBlocking {
                    @Suppress("UNUSED_VARIABLE")
                    val unused = Unit
                }
            }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("already open")
        }
    }

    @Test
    fun `withScopeBlocking - the scope is closed after the block returns`() {
        val injector = Guice.createInjector(ScopeBindingModule())
        testContext.withScopeBlocking {
            injector.getInstance(TestScopedService::class.java)
        }
        assertThatThrownBy {
            injector.getInstance(TestScopedService::class.java)
        }.isInstanceOf(ProvisionException::class.java).hasRootCauseInstanceOf(OutOfScopeException::class.java)
    }

    @Test
    fun `withScopeBlocking - the scope is closed after the block throws`() {
        val injector = Guice.createInjector(ScopeBindingModule())
        runCatching {
            testContext.withScopeBlocking {
                injector.getInstance(TestScopedService::class.java)
                error("intentional failure inside the block")
            }
        }
        assertThatThrownBy {
            injector.getInstance(TestScopedService::class.java)
        }.isInstanceOf(ProvisionException::class.java).hasRootCauseInstanceOf(OutOfScopeException::class.java)
    }

    @Test
    suspend fun `currentCoroutineElement - applied to a foreign coroutine makes the scope active there`() {
        val injector = Guice.createInjector(ScopeBindingModule())
        testContext.withScope {
            val resolvedInside = injector.getInstance(TestScopedService::class.java)
            val element = testContext.currentCoroutineElement()
            val resolvedAcross = kotlinx.coroutines.withContext(element) {
                injector.getInstance(TestScopedService::class.java)
            }
            assertThat(resolvedAcross).isSameAs(resolvedInside)
        }
    }

    @Test
    fun `currentCoroutineElement - throws OutOfScopeException when called outside a scope`() {
        assertThatThrownBy {
            testContext.currentCoroutineElement()
        }.isInstanceOf(OutOfScopeException::class.java)
    }
}
