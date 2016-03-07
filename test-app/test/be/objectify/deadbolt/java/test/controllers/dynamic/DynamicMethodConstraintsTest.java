package be.objectify.deadbolt.java.test.controllers.dynamic;

import be.objectify.deadbolt.java.test.DataLoader;
import com.jayway.restassured.RestAssured;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

public class DynamicMethodConstraintsTest
{

    private static final int PORT = 3333;

    @Before
    public void setUp()
    {
        RestAssured.port = PORT;
    }

    @Test
    public void protectedByMethodLevelDynamic_noSubjectIsPresent()
    {
        running(testServer(PORT,
                           fakeApplication()),
                () -> {
                    RestAssured.given()
                               .cookie("user", "greet")
                               .expect()
                               .statusCode(401)
                               .when()
                               .get("/dynamic/m/niceName");
                });
    }

    @Test
    public void protectedByMethodLevelDynamic_subjectHasPermission()
    {
        running(testServer(PORT,
                           fakeApplication(Collections.emptyMap(),
                                           new DataLoader("/be/objectify/deadbolt/java/test/standard.xml"))),
                () -> {
                    RestAssured.given()
                               .cookie("user", "steve")
                               .expect()
                               .statusCode(401)
                               .when()
                               .get("/dynamic/m/niceName");
                });
    }


    @Test
    public void protectedByMethodLevelDynamic_subjectDoesNotHavePermission()
    {
        running(testServer(PORT,
                           fakeApplication(Collections.emptyMap(),
                                           new DataLoader("/be/objectify/deadbolt/java/test/standard.xml"))),
                () -> {
                    RestAssured.given()
                               .cookie("user", "greet")
                               .expect()
                               .statusCode(200)
                               .when()
                               .get("/dynamic/m/niceName");
                });
    }

    @Test
    public void protectedByMethodLevelDynamic_noSubjectIsPresent_controllerExplicitlyUnrestricted()
    {
        running(testServer(PORT,
                           fakeApplication()),
                () -> {
                    RestAssured.given()
                               .cookie("user", "greet")
                               .expect()
                               .statusCode(401)
                               .when()
                               .get("/dynamic/m/niceNameInUnrestrictedController");
                });
    }

    @Test
    public void protectedByMethodLevelDynamic_subjectHasPermission_controllerExplicitlyUnrestricted()
    {
        running(testServer(PORT,
                           fakeApplication(Collections.emptyMap(),
                                           new DataLoader("/be/objectify/deadbolt/java/test/standard.xml"))),
                () -> {
                    RestAssured.given()
                               .cookie("user", "steve")
                               .expect()
                               .statusCode(401)
                               .when()
                               .get("/dynamic/m/niceNameInUnrestrictedController");
                });
    }

    @Test
    public void protectedByMethodLevelDynamic_subjectDoesNotHavePermission_controllerExplicitlyUnrestricted()
    {
        running(testServer(PORT,
                           fakeApplication(Collections.emptyMap(),
                                           new DataLoader("/be/objectify/deadbolt/java/test/standard.xml"))),
                () -> {
                    RestAssured.given()
                               .cookie("user", "greet")
                               .expect()
                               .statusCode(200)
                               .when()
                               .get("/dynamic/m/niceNameInUnrestrictedController");
                });
    }
}
