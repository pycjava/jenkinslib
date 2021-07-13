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
    def servers = ['test':'sonarqube-scanner', 'prod':'sonarqube-scanner']
    withSonarQubeEnv("${servers[sonarServer]}") {
        // If you have configured more than one global server connection, you can specify its name
        //sh "${home}/bin/${buildType} clean verify  -Dmaven.test.skip=true sonar:sonar"
        // def scannerHome = '/home/jenkins/buildtools/sonar-scanner-4.6.2.2472-linux/'
        // def sonarDate = sh  returnStdout: true, script: 'date  +%Y%m%d%H%M%S'
        sh """
            ${home}/bin/${buildType} clean verify  -Dmaven.test.skip=true sonar:sonar
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
def build(buildType, buildShell) {
    def home = buildHome(buildType)
    sh "${home}/bin/${buildType}  ${buildShell}"
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
