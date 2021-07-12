package org.devops

//格式化输出
def PrintMes(value, color) {
    colors = ['red'   : "\033[40;31m >>>>>>>>>>>${value}<<<<<<<<<<< \033[0m",
              'blue'  : "\033[47;34m ${value} \033[0m",
              'green' : "[1;32m>>>>>>>>>>${value}>>>>>>>>>>[m",
              'green1': "\033[40;32m >>>>>>>>>>>${value}<<<<<<<<<<< \033[0m"]
    ansiColor('xterm') {
        println(colors[color])
    }
}

def checkOut(srcUrl, branchName) {
    checkout([$class                           : 'GitSCM', branches: [[name: "${branchName}"]],
              doGenerateSubmoduleConfigurations: false,
              extensions                       : [],
              submoduleCfg                     : [],
              userRemoteConfigs                : [[credentialsId: '0fb2d9d5-ee9e-4901-bf65-9a613a679871', url: "${srcUrl}"]]]) //credentialsId jenkins凭据模式
}

def sonarScan(sonarServer, projectName, projectDesc, projectPath, branchName) {
    def servers = ['test':'sonarqube-test', 'prod':'sonarqube-prod']
    withSonarQubeEnv("${servers[sonarServer]}") {
        // If you have configured more than one global server connection, you can specify its name
        //sh "${home}/bin/${buildType} clean verify  -Dmaven.test.skip=true sonar:sonar"
        def scannerHome = '/home/jenkins/buildtools/sonar-scanner-3.2.0.1227-linux/'
        def sonarDate = sh  returnStdout: true, script: 'date  +%Y%m%d%H%M%S'
        sonarDate = sonarDate - '\n'
        sh """
            ${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${projectName} \
            -Dsonar.projectName=${projectName} -Dsonar.projectVersion=${sonarDate} -Dsonar.ws.timeout=30 \
            -Dsonar.projectDescription=${projectDesc} -Dsonar.links.homepage=http://www.baidu.com \
            -Dsonar.sources=${projectPath} -Dsonar.sourceEncoding=UTF-8 -Dsonar.java.binaries=target/classes \
            -Dsonar.java.test.binaries=target/test-classes -Dsonar.java.surefire.report=target/surefire-reports  -Dsonar.branch.name=${branchName} -X
        """
    }
}

def buildHome(buildType) {
    def buildTools = ['mvn': 'M3', 'ant': 'ANT', 'gradle': 'GRADLE', 'npm': 'NPM']
    println("当前选择的构建类型为 ${buildType}")
    def home = tool buildTools[buildType]
    return home
}

//构建类型
def build(buildType, repositoryUrl) {
    def jarName = sh returnStdout: true, script: 'cd target;ls *.jar'
    jarName = jarName - '\n'
    def pom = readMavenPom file: 'pom.xml'
    pomVersion = "${pom.version}"
    pomArtifact = "${pom.artifactId}"
    pomPackaging = "${pom.packaging}"
    pomGroupId = "${pom.groupId}"
    println("${pomGroupId}-${pomArtifact}-${pomVersion}-${pomPackaging}")
    def home = buildHome(buildType)

    sh """
        ${home}/bin/${buildType}  deploy:deploy-file -Dmaven.test.skip=true \
                                  -Dfile=${jarName} -DgroupId=${pomGroupId} \
                                  -DartifactId=${pomArtifact} -Dversion=${pomVersion}  \
                                  -Dpackaging=${pomPackaging} -DrepositoryId=maven-hostd \
                                  -Durl=${repositoryUrl}

    """
}

//获取POM中的坐标
def GetGav() {
    def pom = readMavenPom file: 'pom.xml'
    env.pomVersion = "${pom.version}"
    env.pomArtifact = "${pom.properties['deploy.packname']}"
    if (pomArtifact == null || pomArtifact == '') {
        env.pomArtifact = "${pom.artifactId}"
    }
    env.pomGroupId = "${pom.groupId}"

    println("${pomGroupId}-${pomArtifact}-${pomVersion}")

    return ["${pomGroupId}", "${pomArtifact}", "${pomVersion}"]
}

def deploy(deployHosts, buildType) {
    GetGav()
    withCredentials([usernamePassword(credentialsId: 'nexus', passwordVariable: 'password', usernameVariable: 'username')]) {
        def repository = 'http://192.168.1.133:8081/repository/maven-releases/'     // nexus maven仓库地址
        println('开始部署')
        if (buildType == 'npm') {
            ansiblePlaybook(
                installation: 'Ansible',
                playbook: '/etc/ansible/npm-deploy.yml',
                extraVars: [
                        groupId:"${pomGroupId}",
                        artifactId:"${pomArtifact}",
                        appVersion:"${pomVersion}",
                        deployIp:"${deployHosts}",
                        repository:"${repository}",
                        username:"${username}",
                        password:"${password}"
                ]
            )
        } else {
            ansiblePlaybook(
                installation: 'Ansible',
                playbook: '/etc/ansible/jar-deploy.yml',
                extraVars: [
                        groupId:"${pomGroupId}",
                        artifactId:"${pomArtifact}",
                        appVersion:"${pomVersion}",
                        deployIp:"${deployHosts}",
                        repository:"${repository}",
                        username:"${username}",
                        password:"${password}"
                ]
            )
        }
    }
}
