properties([
    [$class: 'BuildConfigProjectProperty', name: '', namespace: '', resourceVersion: '', uid: ''],
    parameters([
        string(name: 'BRANCH', defaultValue: 'master', description: '', trim: false),
        string(name: 'GIT_CREDENTIALS', defaultValue: 'jenkins-git-credentials', description: '', trim: false),
        string(name: 'SOURCES_URL', defaultValue: '', description: '', trim: false),
        string(name: 'APPLICATION_NAME', defaultValue: '', description: ''),
    ])
])
boolean exists(String type, String selector) {
    return openshift.selector(type, selector).exists()
}
node {
    timeout(time: 30, unit: 'MINUTES') {

        stage('Git Clone Project') {
            git credentialsId: "${params.GIT_CREDENTIALS}", branch: "${params.BRANCH}", url: "${params.SOURCES_URL}"
            script {
                build_params = readProperties(file: "params/build.params")
            }
        } //stage

        stage('Create imagestreams needed to build images') {
            openshift.withCluster() {
                
                openshift.apply(openshift.process(readFile('openshift/wordpress/imagestream.yaml'),
                "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}"))
                    println "${params.APPLICATION_NAME} imagestream created"
                }
        } //stage
        stage('Create and run buildconfigs') {
            openshift.withCluster() {
                // buildconfig in template to allow pass parameter          
                openshift.apply(openshift.process(readFile('openshift/wordpress/buildConfig.yaml'),
                    "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}",
                    "--param", "QUICKSTART_REPOSITORY_URL=${build_params.QUICKSTART_REPOSITORY_URL}",
                    "--param", "PHP_VERSION=${build_params.PHP_VERSION}"))

                println "buildconfig ${params.APPLICATION_NAME} created"
                }
            } //stage           
        } //timeout
    } //node
