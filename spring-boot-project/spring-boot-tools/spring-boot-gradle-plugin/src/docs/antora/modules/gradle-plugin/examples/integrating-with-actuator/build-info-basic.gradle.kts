plugins {
	java
	id("org.springframework.boot") version "{version-spring-boot}"
}

// tag::build-info[]
springBoot {
	buildInfo()
}
// end::build-info[]
