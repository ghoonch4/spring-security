/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.config.annotation.web

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.test.SpringTestContext
import org.springframework.security.config.test.SpringTestContextExtension
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.security.web.csrf.DefaultCsrfToken
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Tests for [CsrfDsl]
 *
 * @author Eleftheria Stein
 */
@ExtendWith(SpringTestContextExtension::class)
class CsrfDslTests {
    @JvmField
    val spring = SpringTestContext(this)

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `POST when CSRF enabled and no CSRF token then forbidden`() {
        this.spring.register(DefaultCsrfConfig::class.java).autowire()

        this.mockMvc.post("/test1")
                .andExpect {
                    status { isForbidden() }
                }
    }

    @Test
    fun `POST when CSRF enabled and CSRF token then status OK`() {
        this.spring.register(DefaultCsrfConfig::class.java, BasicController::class.java).autowire()

        this.mockMvc.post("/test1") {
            with(csrf())
        }.andExpect {
            status { isOk() }
        }

    }

    @EnableWebSecurity
    open class DefaultCsrfConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            http {
                csrf { }
            }
        }
    }

    @Test
    fun `POST when CSRF disabled and no CSRF token then status OK`() {
        this.spring.register(CsrfDisabledConfig::class.java, BasicController::class.java).autowire()

        this.mockMvc.post("/test1")
                .andExpect {
                    status { isOk() }
                }
    }

    @EnableWebSecurity
    open class CsrfDisabledConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            http {
                csrf {
                    disable()
                }
            }
        }
    }

    @Test
    fun `CSRF when custom CSRF token repository then repo used`() {
        this.spring.register(CustomRepositoryConfig::class.java).autowire()
        mockkObject(CustomRepositoryConfig.REPO)
        every {
            CustomRepositoryConfig.REPO.loadToken(any())
        } returns DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token")

        this.mockMvc.get("/test1")

        verify(exactly = 1) { CustomRepositoryConfig.REPO.loadToken(any()) }
    }

    @EnableWebSecurity
    open class CustomRepositoryConfig : WebSecurityConfigurerAdapter() {

        companion object {
            val REPO: CsrfTokenRepository = HttpSessionCsrfTokenRepository()
        }

        override fun configure(http: HttpSecurity) {
            http {
                csrf {
                    csrfTokenRepository = REPO
                }
            }
        }
    }

    @Test
    fun `CSRF when require CSRF protection matcher then CSRF protection on matching requests`() {
        this.spring.register(RequireCsrfProtectionMatcherConfig::class.java, BasicController::class.java).autowire()

        this.mockMvc.post("/test1")
                .andExpect {
                    status { isForbidden() }
                }

        this.mockMvc.post("/test2")
                .andExpect {
                    status { isOk() }
                }
    }

    @EnableWebSecurity
    open class RequireCsrfProtectionMatcherConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            http {
                csrf {
                    requireCsrfProtectionMatcher = AntPathRequestMatcher("/test1")
                }
            }
        }
    }

    @Test
    fun `CSRF when custom session authentication strategy then strategy used`() {
        this.spring.register(CustomStrategyConfig::class.java).autowire()
        mockkObject(CustomStrategyConfig.STRATEGY)
        every { CustomStrategyConfig.STRATEGY.onAuthentication(any(), any(), any()) } returns Unit

        this.mockMvc.perform(formLogin())

        verify(exactly = 1) { CustomStrategyConfig.STRATEGY.onAuthentication(any(), any(), any()) }

    }

    @EnableWebSecurity
    open class CustomStrategyConfig : WebSecurityConfigurerAdapter() {

        companion object {
            var STRATEGY: SessionAuthenticationStrategy = NullAuthenticatedSessionStrategy()
        }

        override fun configure(http: HttpSecurity) {
            http {
                formLogin { }
                csrf {
                    sessionAuthenticationStrategy = STRATEGY
                }
            }
        }

        @Bean
        override fun userDetailsService(): UserDetailsService {
            val userDetails = User.withDefaultPasswordEncoder()
                    .username("user")
                    .password("password")
                    .roles("USER")
                    .build()
            return InMemoryUserDetailsManager(userDetails)
        }
    }

    @Test
    fun `CSRF when ignoring request matchers then CSRF disabled on matching requests`() {
        this.spring.register(IgnoringRequestMatchersConfig::class.java, BasicController::class.java).autowire()

        this.mockMvc.post("/test1")
                .andExpect {
                    status { isForbidden() }
                }

        this.mockMvc.post("/test2")
                .andExpect {
                    status { isOk() }
                }
    }

    @EnableWebSecurity
    open class IgnoringRequestMatchersConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            http {
                csrf {
                    requireCsrfProtectionMatcher = AntPathRequestMatcher("/**")
                    ignoringRequestMatchers(AntPathRequestMatcher("/test2"))
                }
            }
        }
    }

    @Test
    fun `CSRF when ignoring ant matchers then CSRF disabled on matching requests`() {
        this.spring.register(IgnoringAntMatchersConfig::class.java, BasicController::class.java).autowire()

        this.mockMvc.post("/test1")
                .andExpect {
                    status { isForbidden() }
                }

        this.mockMvc.post("/test2")
                .andExpect {
                    status { isOk() }
                }
    }

    @EnableWebSecurity
    open class IgnoringAntMatchersConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            http {
                csrf {
                    requireCsrfProtectionMatcher = AntPathRequestMatcher("/**")
                    ignoringAntMatchers("/test2")
                }
            }
        }
    }

    @RestController
    internal class BasicController {
        @PostMapping("/test1")
        fun test1() {
        }

        @PostMapping("/test2")
        fun test2() {
        }
    }
}
