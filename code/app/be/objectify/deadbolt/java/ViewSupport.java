/*
 * Copyright 2010-2016 Steve Chaloner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package be.objectify.deadbolt.java;

import be.objectify.deadbolt.java.cache.HandlerCache;
import be.objectify.deadbolt.java.models.PatternType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Configuration;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Provides the entry point for view-level annotations.
 *
 * @author Steve Chaloner (steve@objectify.be)
 */
@Singleton
public class ViewSupport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ViewSupport.class);

    public final Supplier<Long> defaultTimeout;

    private final HandlerCache handlerCache;

    private final TemplateFailureListener failureListener;

    private final BiFunction<Long, TimeoutException, Boolean> timeoutHandler;

    private final ConstraintLogic constraintLogic;

    @Inject
    public ViewSupport(final Configuration configuration,
                       final HandlerCache handlerCache,
                       final TemplateFailureListenerProvider failureListener,
                       final ConstraintLogic constraintLogic)
    {
        this.handlerCache = handlerCache;
        this.failureListener = failureListener.get();
        this.constraintLogic = constraintLogic;


        final Long timeout = configuration.getLong(ConfigKeys.DEFAULT_VIEW_TIMEOUT_DEFAULT._1,
                                                   ConfigKeys.DEFAULT_VIEW_TIMEOUT_DEFAULT._2);
        LOGGER.info("Default timeout period for blocking views is [{}]ms",
                    timeout);
        this.defaultTimeout = () -> timeout;

        timeoutHandler = (timeoutInMillis, e) ->
        {
            LOGGER.error("Timeout when attempting to complete future within [{}]ms.  Denying access to resource.",
                         timeoutInMillis,
                         e);
            this.failureListener.failure("Error when checking view constraint: " + e.getMessage(),
                                         timeoutInMillis);
            return false;
        };
    }

    /**
     * Used for restrict tags in the template.
     *
     * @param roles a list of String arrays.  Within an array, the roles are ANDed.  The arrays in the list are OR'd.
     * @return true if the view can be accessed, otherwise false
     */
    public boolean viewRestrict(final List<String[]> roles,
                                final DeadboltHandler handler,
                                final Optional<String> content,
                                final long timeoutInMillis) throws Throwable
    {
        boolean allowed;
        try
        {
            allowed = constraintLogic.restrict(Http.Context.current(),
                                               handler(handler),
                                               content,
                                               () -> roles,
                                               ctx -> CompletableFuture.completedFuture(Boolean.TRUE),
                                               (ctx, dh, cnt) -> CompletableFuture.completedFuture(Boolean.FALSE))
                                     .toCompletableFuture()
                                     .get(timeoutInMillis,
                                          TimeUnit.MILLISECONDS);

        }
        catch (TimeoutException e)
        {
            allowed = timeoutHandler.apply(timeoutInMillis,
                                           e);
        }
        return allowed;
    }

    /**
     * Used for dynamic tags in the template.
     *
     * @param name the name of the resource
     * @param meta meta information on the resource
     * @return true if the view can be accessed, otherwise false
     */
    public boolean viewDynamic(final String name,
                               final Optional<String> meta,
                               final DeadboltHandler handler,
                               final Optional<String> content,
                               final long timeoutInMillis) throws Throwable
    {
        boolean allowed;
        try
        {
            allowed = constraintLogic.dynamic(Http.Context.current(),
                                              handler(handler),
                                              content,
                                              name,
                                              meta,
                                              ctx -> CompletableFuture.completedFuture(Boolean.TRUE),
                                              (ctx, dh, cnt) -> CompletableFuture.completedFuture(Boolean.FALSE))
                                     .toCompletableFuture()
                                     .get(timeoutInMillis,
                                          TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            allowed = timeoutHandler.apply(timeoutInMillis,
                                           e);
        }
        return allowed;
    }

    /**
     * Used for subjectPresent tags in the template.
     *
     * @return true if the view can be accessed, otherwise false
     */
    public boolean viewSubjectPresent(final DeadboltHandler handler,
                                      final Optional<String> content,
                                      final long timeoutInMillis) throws Throwable
    {
        boolean allowed;
        try
        {
            allowed = constraintLogic.subjectPresent(Http.Context.current(),
                                                     handler == null ? handlerCache.get()
                                                                     : handler,
                                                     content,
                                                     (ctx, dh, cnt) -> CompletableFuture.completedFuture(Boolean.TRUE),
                                                     (ctx, dh, cnt) -> CompletableFuture.completedFuture(Boolean.FALSE))
                                     .toCompletableFuture()
                                     .get(timeoutInMillis,
                                          TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            allowed = timeoutHandler.apply(timeoutInMillis,
                                           e);
        }
        return allowed;
    }

    /**
     * Used for subjectNotPresent tags in the template.
     *
     * @return true if the view can be accessed, otherwise false
     */
    public boolean viewSubjectNotPresent(final DeadboltHandler handler,
                                         final Optional<String> content,
                                         final long timeoutInMillis) throws Throwable
    {
        boolean allowed;
        try
        {
            allowed = constraintLogic.subjectPresent(Http.Context.current(),
                                                     handler(handler),
                                                     content,
                                                     (ctx, dh, cnt) -> CompletableFuture.completedFuture(Boolean.FALSE),
                                                     (ctx, dh, cnt) -> CompletableFuture.completedFuture(Boolean.TRUE))
                                     .toCompletableFuture()
                                     .get(timeoutInMillis,
                                          TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            allowed = timeoutHandler.apply(timeoutInMillis,
                                           e);
        }
        return allowed;
    }

    public boolean viewPattern(final String value,
                               final PatternType patternType,
                               final Optional<String> meta,
                               final boolean invert,
                               final DeadboltHandler handler,
                               final Optional<String> content,
                               final long timeoutInMillis) throws Exception
    {
        boolean allowed;
        try
        {
            allowed = constraintLogic.pattern(Http.Context.current(),
                                              handler(handler),
                                              content,
                                              value,
                                              patternType,
                                              meta,
                                              invert,
                                              ctx -> CompletableFuture.completedFuture(Boolean.TRUE),
                                              (ctx, dh, cnt) -> CompletableFuture.completedFuture(Boolean.FALSE))
                                     .toCompletableFuture()
                                     .get(timeoutInMillis,
                                          TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            allowed = timeoutHandler.apply(timeoutInMillis,
                                           e);
        }

        return allowed;
    }

    /**
     * Used for role-based permissions tags in the template.
     *
     * @param roleName the role name that is the key for the permission set
     * @return true if the view can be accessed, otherwise false
     */
    public boolean viewRoleBasedPermissions(final String roleName,
                                            final DeadboltHandler handler,
                                            final Optional<String> content,
                                            final long timeoutInMillis) throws Throwable
    {
        boolean allowed;
        try
        {
            allowed = constraintLogic.roleBasedPermissions(Http.Context.current(),
                                                           handler(handler),
                                                           content,
                                                           roleName,
                                                           ctx -> CompletableFuture.completedFuture(Boolean.TRUE),
                                                           (ctx, dh, cnt) -> CompletableFuture.completedFuture(Boolean.FALSE))
                                     .toCompletableFuture()
                                     .get(timeoutInMillis,
                                          TimeUnit.MILLISECONDS);

        }
        catch (TimeoutException e)
        {
            allowed = timeoutHandler.apply(timeoutInMillis,
                                           e);
        }
        return allowed;
    }

    private DeadboltHandler handler(final DeadboltHandler handler)
    {
        return handler == null ? handlerCache.get()
                               : handler;
    }
}