#!/bin/bash

# Project name
PROJECT_NAME="JavaCodexProject"

# Create Maven project structure
mvn archetype:generate -DgroupId=com.codex.app -DartifactId=$PROJECT_NAME -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false

cd $PROJECT_NAME

# Add dependencies to pom.xml
cat <<EOF > pom.xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.codex.app</groupId>
    <artifactId>$PROJECT_NAME</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>uk.co.caprica</groupId>
            <artifactId>vlcj</artifactId>
            <version>4.7.1</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>17.0.6</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.discord</groupId>
            <artifactId>rpc</artifactId>
            <version>1.6.2</version>
        </dependency>
        <dependency>
            <groupId>net.java.dev.jna</groupId>
            <artifactId>jna</artifactId>
            <version>5.13.0</version>
        </dependency>
    </dependencies>
</project>
EOF

echo "✅ Project '$PROJECT_NAME' created. You can now open it in VS Code!"
