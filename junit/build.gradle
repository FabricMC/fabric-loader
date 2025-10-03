apply plugin: 'maven-publish'
apply plugin: 'me.modmuss50.remotesign'

base {
	archivesName = "fabric-loader-junit"
}

version = rootProject.version
group = rootProject.group

def ENV = System.getenv()
def signingEnabled = ENV.SIGNING_SERVER

repositories {
	mavenCentral()
}

dependencies {
	api project(":")

	api platform("org.junit:junit-bom:5.10.0")
	api "org.junit.jupiter:junit-jupiter-engine"
	implementation "org.junit.platform:junit-platform-launcher"
}

java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_1_8
	targetCompatibility = JavaVersion.VERSION_1_8
}

jar {
	manifest {
		attributes 'Automatic-Module-Name': 'net.fabricmc.loader.junit'
	}
}

if (signingEnabled) {
	remoteSign {
		requestUrl = ENV.SIGNING_SERVER
		pgpAuthKey = ENV.SIGNING_PGP_KEY
		jarAuthKey = ENV.SIGNING_JAR_KEY

		afterEvaluate {
			sign publishing.publications.maven
		}
	}
}

publishing {
	publications {
		maven(MavenPublication) {
			artifactId = project.base.archivesName.get()
			from components.java
		}
	}

	repositories {
		if (ENV.MAVEN_URL) {
			maven {
				url ENV.MAVEN_URL
				credentials {
					username ENV.MAVEN_USERNAME
					password ENV.MAVEN_PASSWORD
				}
			}
		}
	}
}