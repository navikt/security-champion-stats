package navikt.appsec.securitychampionstats

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication


@SpringBootApplication
class AppStarter

fun main(args: Array<String>) {
    SpringApplication.run(AppStarter::class.java, *args)
}