/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.openig.filter.oauth2.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.forgerock.openig.filter.oauth2.AccessToken;
import org.forgerock.openig.filter.oauth2.AccessTokenResolver;
import org.forgerock.openig.filter.oauth2.OAuth2TokenException;
import org.forgerock.openig.util.ThreadSafeCache;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CachingAccessTokenResolverTest {

    @Mock
    private AccessTokenResolver resolver;

    @Mock
    private ScheduledExecutorService executorService;

    // Don't know why but if I @Spy this field, Mockito does not re-creates the cache instance, leading to errors
    private ThreadSafeCache<String, Promise<AccessToken, OAuth2TokenException>> cache;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(resolver.resolve(any(Context.class), anyString()))
                .thenReturn(Promises.<AccessToken, OAuth2TokenException>newResultPromise(null));
        cache = spy(new ThreadSafeCache<String, Promise<AccessToken, OAuth2TokenException>>(executorService));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldUseCache() throws Exception {
        CachingAccessTokenResolver caching = new CachingAccessTokenResolver(resolver, cache);

        Promise<AccessToken, OAuth2TokenException> p1 = caching.resolve(new RootContext(), "TOKEN");
        Promise<AccessToken, OAuth2TokenException> p2 = caching.resolve(new RootContext(), "TOKEN");

        assertThat(p1.get()).isSameAs(p2.get());
        verify(cache, times(2)).getValue(eq("TOKEN"), any(Callable.class), any(AsyncFunction.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldUseExpiresAttribute() throws Exception {
        AccessToken token = mock(AccessToken.class);
        when(token.getExpiresAt()).thenReturn(42L);
        when(resolver.resolve(any(Context.class), eq("TOKEN")))
                .thenReturn(Promises.<AccessToken, OAuth2TokenException>newResultPromise(token));

        CachingAccessTokenResolver caching = new CachingAccessTokenResolver(resolver, cache);

        AccessToken accessToken = caching.resolve(new RootContext(), "TOKEN").get();

        verify(executorService).schedule(any(Callable.class), eq(42L), eq(TimeUnit.MILLISECONDS));
    }

}
