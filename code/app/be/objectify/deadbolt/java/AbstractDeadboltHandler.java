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

import be.objectify.deadbolt.java.models.Subject;
import play.libs.concurrent.HttpExecution;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;
import views.html.defaultpages.unauthorized;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Abstract implementation of {@link DeadboltHandler} that gives a standard unauthorised result when access fails.
 *
 * @author Steve Chaloner (steve@objectify.be)
 */
public abstract class AbstractDeadboltHandler extends Results implements DeadboltHandler
{
    protected final DeadboltExecutionContextProvider executionContextProvider;

    public AbstractDeadboltHandler(final ExecutionContextProvider ecProvider)
    {
        this.executionContextProvider = ecProvider.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<Optional<Result>> beforeAuthCheck(Http.Context context)
    {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<Optional<? extends Subject>> getSubject(final Http.Context context)
    {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<Result> onAuthFailure(final Http.Context context,
                                                 final Optional<String> content)
    {
        final ExecutionContext executionContext = executionContextProvider.get();
        final ExecutionContextExecutor executor = HttpExecution.fromThread(executionContext);
        return CompletableFuture.supplyAsync(unauthorized::render,
                                             executor)
                                .thenApplyAsync(Results::unauthorized,
                                                executor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<Optional<DynamicResourceHandler>> getDynamicResourceHandler(final Http.Context context)
    {
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
