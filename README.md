# Deadbolt 2 for Java 2.4

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/be.objectify/deadbolt-java_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/be.objectify/deadbolt-java_2.11) [![Build Status](https://travis-ci.org/schaloner/deadbolt-2-java.svg?branch=2.4.x)](https://travis-ci.org/schaloner/deadbolt-2-java) [![Join the chat at https://gitter.im/schaloner/deadbolt-2](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/schaloner/deadbolt-2?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


Idiomatic Java API for Deadbolt 2, an authorisation module for Play 2.

Deadbolt 2 comprises of several modules - a common core, and language-specific implementations for Java and Scala.  Example applications and a user guide are also available.  

All modules related to Deadbolt 2, including the user guide, are grouped together in the [Deadbolt 2](https://github.com/schaloner/deadbolt-2) Github super-module.  Installation information, including Deadbolt/Play compatibility, can also be found here.

# Quickstart

Deadbolt 2 is an authorization library for Play 2, and features APIs for both Java- and Scala-based applications.  It allows you to apply constraints to controller actions, and to customize template rendering based on the current user.

Here's a short, fast guide to getting started with the Java version.

## Dependencies

Add the dependency to your build

    `"be.objectify" %% "deadbolt-java" % "2.4.4"`

Add the Deadbolt module to your Play application

    play {
      modules {
        enabled += be.objectify.deadbolt.java.DeadboltModule
      }
    }


## Domain model

Implement the `Subject`, `Role` and `Permission` interfaces.

- `Subject` represents, typically, a user
- A `Role` is a single system privilege, e.g. **admin**, **user** and so on.  A subject can have zero or more roles.
- A `Permission` is a can be used with regular expression matching, e.g. a subject with a permission of `printers.admin` can access a resource constrained to `printers.*`, `*.admin`, etc.    A subject can have zero or more permissions.

## Hooks

Implement the `be.objectify.deadbolt.java.DeadboltHandler` interface.  This implementation (or implementations - you can more than one) is used to

 - get the current user - `getSubject`
 - run a pre-authorization task that can block further execution - `beforeAuthCheck`
 - handle authorization failure - `onAuthFailure`
 - provide a hook into the dynamic constraint types - `getDynamicResourceHandler`

You only need to implement `be.objectify.deadbolt.java.DynamicResourceHandler` if you're planning to use `Dynamic` or `Pattern.CUSTOM` constraints.  This will be covered in detail in another section.


Implement the `be.objectify.deadbolt.java.HandlerCache` interface.  This is used by Deadbolt to obtain instances of `DeadboltHandler`s, and has two concepts

1. A default handler.  You can always use a specific handler in a template or controller, but if nothing is specified a well-known instance will be used.
2. Named handlers.  

An example implementation follows, based on the [sample app](https://github.com/schaloner/deadbolt-2-java-examples).  You can, of course, define these any way you want to.

    @Singleton
    public class MyHandlerCache implements HandlerCache
    {
        private final DeadboltHandler defaultHandler = new MyDeadboltHandler();
        private final Map<String, DeadboltHandler> handlers = new HashMap<>();

        public MyHandlerCache()
        {
            handlers.put(HandlerKeys.DEFAULT.key, defaultHandler);
            handlers.put(HandlerKeys.ALT.key, new MyAlternativeDeadboltHandler());
            handlers.put(HandlerKeys.BUGGY.key, new BuggyDeadboltHandler());
            handlers.put(HandlerKeys.NO_USER.key, new NoUserDeadboltHandler());
        }

        @Override
        public DeadboltHandler apply(final String key)
        {
            return handlers.get(key);
        }

        @Override
        public DeadboltHandler get()
        {
            return defaultHandler;
        }
    }


Finally, expose your handlers to Deadbolt.  To do this, you will need to create a small module that binds your handler cache by type...

    package com.example.modules

    import be.objectify.deadbolt.java.cache.HandlerCache;
    import play.api.Configuration;
    import play.api.Environment;
    import play.api.inject.Binding;
    import play.api.inject.Module;
    import scala.collection.Seq;
    import security.MyHandlerCache;

    import javax.inject.Singleton;

    public class CustomDeadboltHook extends Module
    {
        @Override
        public Seq<Binding<?>> bindings(final Environment environment,
                                        final Configuration configuration)
        {
            return seq(bind(HandlerCache.class).to(MyHandlerCache.class).in(Singleton.class));
        }
    }

...and add it to your application.conf

    play {
        modules {
            enabled += be.objectify.deadbolt.java.DeadboltModule
            enabled += com.example.modules.CustomDeadboltHook
        }
    }

You're now ready to secure access to controller functions and templates in your Play 2 application.

## Controller constraints with the action builder

Controller constraints are defined through annotations.

### SubjectPresent and SubjectNotPresent

Sometimes, you don't need fine-grained checked - you just need to see if there is a user present (or not present)

    @SubjectPresent
    public F.Promise<Result> someMethodA() {
        // method will execute if the current DeadboltHandler's getSubject returns Some
    }

    @SubjectNotPresent
    public F.Promise<Result> someMethodB() {
        // method will execute if the current DeadboltHandler's getSubject returns None
    }

### Restrict

This uses the `Subject`s `Role`s to perform AND/OR/NOT checks.  The values given to the builder must match the `Role.name` of the subject's roles.

AND is defined as an `@Group` OR is an array of `@Group`, and NOT is a rolename with a `!` preceding it.

    @Restrict(@Group("foo"))
    public F.Promise<Result> someMethodA() {
        // method will execute of subject has the "foo" role
    }

    @Restrict(@Group("foo", "bar"))
    public F.Promise<Result> someMethodB() {
        // method will execute of subject has the "foo" AND "bar" roles
    }

    @Restrict({@Group("foo"), @Group("bar")})
    public F.Promise<Result> someMethodC() {
        // method will execute of subject has the "foo"OR "bar" roles
    }

### Pattern

This uses the `Subject`s `Permission`s to perform a variety of checks.  

    @Pattern("admin.printer")
    public F.Promise<Result> someMethodA() {
        // subject must have a permission with the exact value "admin.printer"
    }

    @Pattern(value = "(.)*\.printer", patternType = PatternType.REGEX)
    public F.Promise<Result> someMethodB() {
        // subject must have a permission that matches the regular expression (without quotes) "(.)*\.printer"
    }

    @Pattern(value = "something arbitrary", patternType = PatternType.CUSTOM)
    public F.Promise<Result> someMethodC() {
        // the checkPermssion method of the current handler's DynamicResourceHandler will be used.  This is a user-defined test
    }

If you want to invert the result, i.e. deny access if there's a match, set the `invert` attribute to `true`.

    @Pattern(value = "admin.printer", invert = true)
    public F.Promise<Result> someMethodA() {
        // subject with a permission with the exact value "admin.printer" will by denied
    }


### Dynamic

The most flexible constraint - this is a completely user-defined constraint that uses `DynamicResourceHandler#isAllowed` to determine access.  

    @Dynamic(name = "name of the test")
    public F.Promise<Result> someMethod() {
        // the method will execute if the user-defined test returns true
    }


## Template constraints

Using template constraints, you can exclude portions of templates from being generated on the server-side.  This is not a client-side DOM manipulation!  Template constraints have the same possibilities as controller constraints.  

By default, template constraints use the default Deadbolt handler but as with controller constraints you can pass in a specific handler.  The cleanest way to do this is to pass the handler into the template and then pass it into the constraints.  Another advantage of this approach is you can pass in a wrapped version of the handler that will cache the subject; if you have a lot of constraints in a template, this can yield a significant gain.

One important thing to note here is that templates are blocking, so any Futures used need to be completed for the resuly to be used in the template constraints.  As a result, each constraint can take a function that expresses a Long, which is the millisecond value of the timeout.  It defaults to 1000 milliseconds, but you can change this globally by setting the `deadbolt.java.view-timeout` value in your `application.conf`.

Each constraint has a variant which allows you to define fallback content.  This comes in the format `<constraintName>Or`, e.g.

    @subjectPresentOr {
      this is protected
    } {
      this will be shown if the constraint blocks the other content
    }

### SubjectPresent

    @import be.objectify.deadbolt.java.views.html.{subjectPresent, subjectPresentOr}
    
    @subjectPresent() {
        This content will be present if handler#getSubject results in a Some 
    }
    
    @subjectPresentOr() {
        This content will be present if handler#getSubject results in a Some 
    } {
      fallback content
    }

### SubjectNotPresent

    @import be.objectify.deadbolt.java.views.html.{subjectNotPresent, subjectNotPresentOr}
    
    @subjectNotPresent() {
        This content will be present if handler#getSubject results in a None 
    }
    
    @subjectNotPresentOr() {
        This content will be present if handler#getSubject results in a None 
    } {
      fallback content
    }

### Restrict

`la` and `as` are convenience functions for creating a `List[Array]` and an `Array[String]`

    @import be.objectify.deadbolt.java.views.html.{restrict, restrictOr}
    @import be.objectify.deadbolt.core.utils.TemplateUtils.{la, as}
    
    @restrict(roles = la(as("foo", "bar"))) {
        Subject requires the foo role for this to be visible
    }
    
    @restrict(roles = la(as("foo", "bar")) {
         Subject requires the foo AND bar roles for this to be visible
    }
    
    @restrict(roles = la(as("foo"), as("bar"))) {
         Subject requires the foo OR bar role for this to be visible
    }
    
    @restrictOr(roles = la(as("foo", "bar"))) {
         Subject requires the foo AND bar roles for this to be visible
    } {
      Subject does not have the necessary roles
    }


### Pattern

 The default pattern type is `PatternType.EQUALITY`.

    @import be.objectify.deadbolt.java.views.html.{pattern, patternOr}
    
    @pattern(value = "admin.printer") {
        Subject must have a permission with the exact value "admin.printer" for this to be visible
    }
    
    @pattern(value = "(.)*\.printer", patternType = PatternType.REGEX) {
      Subject must have a permission that matches the regular expression (without quotes) "(.)*\.printer" for this to be visible
    }
    
    @pattern(value = "something arbitrary", patternType = PatternType.CUSTOM) {
      DynamicResourceHandler#checkPermission must result in true for this to be visible
    }
    
    @patternOr(value = "admin.printer") {
        Subject must have a permission with the exact value "admin.printer" for this to be visible
    } {
      Subject did not have necessary permissions
    }

### Dynamic

    @import be.objectify.deadbolt.java.views.html.{dynamic, dynamicOr}
    
    @dynamic(name = "someName") {
        DynamicResourceHandler#isAllowed must result in true for this to be visible
    }
    
    @dynamicOr(name = "someName") {
        DynamicResourceHandler#isAllowed must result in true for this to be visible
    } {
      Custom test failed
    }


## JPA

If you're using JPA, there's a good chance you're going to run into issues when entity managers are used across different threads.  To address this, you can put Deadbolt into blocking mode - this ensures all DB calls made in the Deadbolt layer are made from the same thread; this has performance implications, but it's unavoidable with JPA.

To switch to blocking mode, set `deadbolt.java.blocking` to `true` in your configuration.

The default timeout is 1000 milliseconds - to change this, use `deadbolt.java.blocking-timeout` in your configuration.

This example configuration puts Deadbolt in blocking mode, with a timeout of 2500 milliseconds:

    deadbolt {
        java {
            blocking=true
            blocking-timeout=2500
        }
    }


## What next?
If you want to explore Deadbolt further, you might want to take a look at the book I'm currently writing on it.  You can find it at [https://leanpub.com/deadbolt-2](https://leanpub.com/deadbolt-2).

![Deadbolt 2 - Powerful authorization for your Play application](http://www.objectify.be/wordpress/wp-content/uploads/2015/09/large.jpg)


2.4.0 Release notes
-------------------

This list is incomplete!

- DeadboltPlugin has been removed - delete its entry in play.plugins
- F.Promise<> is now used by `DeadboltHandler` and `DynamicResourceHandler`.
- Don't return `null` where an `F.Promise` or `Optional` is required!

Finally, enable the DeadboltModule in application.conf

    play {
      modules {
        enabled += "be.objectify.deadbolt.java.DeadboltModule"
      }
    }


2.3.3 Release notes
-------------------

The primary focus of 2.3.3 was to move to a non-blocking architecture.  A simplification of structure was also needed, so a change to the DeadboltHandler interface in release 2.3.2 has been backed out; sorry about that.  Semantic versioning will continue shortly.

So, practical changes.

- DeadboltHandler#getSubject returns a Subject in place of an F.Promise<Subject>.  Where the subject is needed, the internal code will take care of wrapping the call in a Promise.
- There are no more timeouts, so deadbolt.before-auth-check-timeout and deadbolt.get-subject-timeout are no longer needed.  If they're defined in your config, they'll be ignored.

**What kind of idiot makes API-level changes in a patch release?**

Me, I'm afraid.  This will probably be the last release of Deadbolt for Play 2.3 and I want to keep the major and minor version of Deadbolt locked into the Play versions.  The change in 2.3.2 was, overall, ill-considered and shouldn't have happened so I'm looking at this as a bug fix more than a change.
