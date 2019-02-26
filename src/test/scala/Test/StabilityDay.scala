/**
 * Copyright 2011-2019 GatlingCorp (http://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package computerdatabase

import io.gatling.core.Predef.{ doIfEqualsOrElse, _ }
import io.gatling.http.Predef.{ status, _ }
import scala.util.Random
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Calendar
import scala.math
import scala.concurrent.duration._

class Stability24hours extends Simulation {

  val httpProtocol = http
    .baseUrl("http://computer-database.gatling.io")
    .inferHtmlResources(BlackList(""".*\.js""", """.*\.css""", """.*\.gif""", """.*\.jpeg""", """.*\.jpg""", """.*\.ico""", """.*\.woff""", """.*\.(t|o)tf""", """.*\.png"""), WhiteList(""".*computer\-database\.gatling\.io.*"""))
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
    .upgradeInsecureRequestsHeader("1")
    .userAgentHeader("Mozilla/5.0 (Windows NT 10.0; WOW64; rv:65.0) Gecko/20100101 Firefox/65.0")

  val feeder = tsv("dates").random
  val headers_10 = Map("Content-Type" -> "application/x-www-form-urlencoded")

  val scn = scenario("RecordedSimulation")
    .feed(feeder)
    .forever(
      exec(http("main page")
        .get("/"))
        .pause(2)
        .exec(http("search random")
          .get("/computers")
          .queryParam("f", x => { Random.alphanumeric.take(1).mkString })
          .check(
            regex("""value="(.+)" place""").saveAs("searchString")
          )
          .check(
            checkIf(
              (r: Response, s: Session) => r.body.string.contains("""<a href="/computers?p=1""")
            )(
                regex("""<a>Displaying 1 to \d+ of (\d+)<\/a>""").saveAs("products_count")
              )
          )
          .check(
            regex("""<a href=.\/computers\/(\d+).>""")
              .findAll
              .saveAs("linkArr")
          ))
        .doIf("${products_count.exists()}") {
          pause(4)
            .exec(
              http("select page if exist from search")
                .get("/computers")
                .queryParam("p", session => { math.ceil(Random.nextInt(session("products_count").as[Int] / 10)).toInt })
                .queryParam("f", "${searchString}")
                .check(
                  regex("""<a href=.\/computers\/(\d+).>""")
                    .findAll
                    .saveAs("linkArr")
                )
            )
        }
        .pause(3)
        .exec(
          http("open pc page")
            .get("/computers/${linkArr.random()}")
            check (
              regex("""<form action="\/computers\/(\d+)""").saveAs("currentId")
            )
        )
        .doIfOrElse(x => { System.currentTimeMillis() % 2 == 0 }) {
          pause(3)
            .exec(http("delete PC")
              .post("/computers/${currentId}/delete")
              .disableFollowRedirect
              .check(headerRegex("Set-Cookie", "PLAY_FLASH=.(.*)=Comp").is("success"))
              .check(status.is(303)))

        } {
          pause(7)
            .exec(http("edit PC")
              .post("/computers/${currentId}")
              .formParam("name", x => { Random.alphanumeric.take(Random.nextInt(35)).mkString })
              .formParam("introduced", x => { new java.text.SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime()) })
              .formParam("discontinued", x => { new java.text.SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().getTime()) })
              .formParam("company", x => { Random.nextInt(40) })
              .disableFollowRedirect
              .check(headerRegex("Set-Cookie", "PLAY_FLASH=.(.*)=Comp").is("success"))
              .check(status.is(303)))
        }
        .doIf(x => { System.currentTimeMillis() % 2 == 0 }) {
          pause(3)
            .exec(http("go to new PC page")
              .get("/computers/new"))
            .pause(7)
            .exec(http("create new PC")
              .post("/computers")
              .headers(headers_10)
              .formParam("name", x => { Random.alphanumeric.take(35).mkString })
              .formParam("introduced", "${randomDate}")
              .formParam("discontinued", "${randomDate}")
              .formParam("company", { Random.nextInt(40) })
              .disableFollowRedirect
              .check(headerRegex("Set-Cookie", "PLAY_FLASH=.(.*)=Comp").is("success"))
              .check(status.is(303)))
        }
    )
  //	setUp(scn.inject(
  //    heavisideUsers(100) during (10 minutes)
  //  ).protocols(httpProtocol))
  //  setUp(scn.inject(
  //    rampUsers(20) over(1 hour),
  //    constantUsersPerSec(20) during (1 hour)
  //  ).protocols(httpProtocol))
  //  setUp(scn.inject(atOnceUsers(10)).protocols(httpProtocol))
  //  setUp(scn.inject(
  //    rampConcurrentUsers(0) to (10) during (1 minutes),
  //    constantConcurrentUsers(10) during (5 minutes)).protocols(httpProtocol)).maxDuration(65 minutes)
  setUp(scn.inject(
    rampConcurrentUsers(0) to (600) during (10 minutes),
    constantConcurrentUsers(600) during (24 hours)
  ).protocols(httpProtocol)).maxDuration(24 hours)
}
