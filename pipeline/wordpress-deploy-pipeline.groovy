properties([
    [$class: 'BuildConfigProjectProperty', name: '', namespace: '', resourceVersion: '', uid: ''],
    parameters([
        string(name: 'BRANCH', defaultValue: 'master', description: ''),
        string(name: 'GIT_CREDENTIALS', defaultValue: 'jenkins-git-credentials', description: ''),
        string(name: 'SOURCES_URL', defaultValue: 'https://git@github.com/sculang/demo-wp', description: ''),
        string(name: 'APPLICATION_NAME', defaultValue: '', description: ''),
        string(name: 'DB_NAME', defaultValue: '', description: ''),
        string(name: 'DEPLOY_PROJECT', defaultValue: '', description: ''),
        string(name: 'APP_HOST', defaultValue: '', description: ''),
        string(name: 'ZIP_LINK_WP', defaultValue: '', description: ''),
        string(name: 'ZIP_LINK_SQL', defaultValue: '', description: ''),
        string(name: 'OLD_URL', defaultValue: '', description: ''),
        string(name: 'DB_TABLES_PREFIX', defaultValue: '', description: ''),        

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
                deploy_params = readProperties(file: "params/deploy.params")//use the file deploy.params in directory params as parameter file 
            }
        }
        stage('cleanup') {
            openshift.withCluster() {
                openshift.withProject("${params.DEPLOY_PROJECT}") {
                    //delete wordpress application deploymentconfig
                    def WordPressdcSelector = openshift.selector('dc', [app: "${params.APPLICATION_NAME}"])
                    if (WordPressdcSelector.exists()) {
                        WordPressdcSelector.delete()
                        println "DeploymentConfig ${params.APPLICATION_NAME} deleted"
                    }
                    //delete databse deploymentconfig
                    def DBdcSelector = openshift.selector('dc', [app: "${params.DB_NAME}"])
                    if (DBdcSelector.exists()) {
                        DBdcSelector.delete()
                        println "DeploymentConfig ${params.DB_NAME} deleted"
                    }
                    //delete configmap 
                   
                    def cm = openshift.selector('cm', [app: "${params.APPLICATION_NAME}"])
                    if (cm.exists()) {
                        cm.delete()
                        println " Configmap deleted"
                    }
                   // delete pvc(persistent volume claim)
                    def WordPresspvcSelector = openshift.selector('pvc', [app: "${params.APPLICATION_NAME}"])
                    if (WordPresspvcSelector.exists()) {
                        WordPresspvcSelector.delete()
                        println "persistent volume ${params.APPLICATION_NAME} deleted"
                    }
                    //delete svc(service)
                    def WordPressSVCSelector = openshift.selector('svc', [app: "${params.APPLICATION_NAME}"])
                    if (WordPressSVCSelector.exists()) {
                        WordPressSVCSelector.delete()
                        println "service ${params.APPLICATION_NAME} deleted"
                    }
                    //delete route
                    def WordPressRouteSelector = openshift.selector('route', [app: "${params.APPLICATION_NAME}"])
                    if (WordPressRouteSelector.exists()) {
                        WordPressRouteSelector.delete()
                        println "route ${params.APPLICATION_NAME} deleted"
                    }
                    // delete pvc(persistent volume claim)
                    def DBpvcSelector = openshift.selector('pvc', [app: "${params.DB_NAME}"])
                    if (DBpvcSelector.exists()) {
                        DBpvcSelector.delete()
                        println "persistent volume ${params.DB_NAME} deleted"
                    }
                    //delete svc(service)
                    def DBsvcSelector = openshift.selector('svc', [app: "${params.DB_NAME}"])
                    if (DBsvcSelector.exists()) {
                        DBsvcSelector.delete()
                        println "service ${params.DB_NAME} deleted"
                    }
                }
            }
        } // stage

        stage('Create Database PVC if it is not already existing') {
            openshift.withCluster() {
                openshift.withProject("${params.DEPLOY_PROJECT}") {
                    if (!exists("pvc", "${params.DB_NAME}")) {
                        openshift.create(openshift.process(readFile('openshift/mysql/pvc_db.yaml'), 
                        "--param", "DATABASE_VOLUME_SIZE=${deploy_params.DATABASE_VOLUME_SIZE}", 
                        "--param", "DB_NAME=${params.DB_NAME}",
                        "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}"))
                        println "pvc ${params.DB_NAME} created"
                    }
                }
            }
        }
        stage('Create Wordpress PVC if it is not already existing') {
            openshift.withCluster() {
                openshift.withProject("${params.DEPLOY_PROJECT}") {
                    if (!exists("pvc", "${params.APPLICATION_NAME}")) {
                        openshift.create(openshift.process(readFile('openshift/wordpress/pvc_wp.yaml'),
                         "--param", "WORDPRESS_VOLUME_SIZE=${deploy_params.WORDPRESS_VOLUME_SIZE}", 
                         "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}"))
                        println "pvc ${params.APPLICATION_NAME} created"
                    }
                }
            }
        }
        //Database Creation
        stage("Create mysql database") {
            openshift.withCluster() {
                openshift.withProject("${params.DEPLOY_PROJECT}") {
                    //if there is a secret existed it will be deleted
                    def secretsSelector = openshift.selector('secret', [app: "${params.APPLICATION_NAME}"])
                    if (secretsSelector.exists()){
                        secretsSelector.delete()
                        println "secret db-secret deleted"
                        }
                    // create secret
                    openshift.create(openshift.process(readFile( "params/secrets/secret.yaml" ),
                        "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}"))
                    println "secret db-secret created"
                    //create database service
                    openshift.create(openshift.process(readFile('openshift/mysql/svc_db.yaml'),
                        "--param", "DB_NAME=${params.DB_NAME}",
                        "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}"))
                    println "service ${params.DB_NAME} created"
                    //create the database
                    openshift.create(openshift.process(readFile('openshift/mysql/DeploymentConfig_db.yaml'),
                        "--param", "MYSQL_VERSION=${deploy_params.MYSQL_VERSION}",
                        "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}",
                        "--param", "DB_NAME=${params.DB_NAME}"))
                    println "deploymentconfig ${params.DB_NAME} created"
                    //check if the database pod running and show the name of the pod
                    echo "waiting for ${params.DB_NAME} to become running"
                    openshift.selector("dc", "${params.DB_NAME}").related('pods').untilEach(1) {
                        def dc0 = it.object()
                        podsname = dc0.metadata.name
                        echo "This is the database pod name : $podsname"
                        return (dc0.status.phase == "Running")
                    }
                }
            }
        }
        // Wordpress creation with all ressources needed
        stage('create Wordpress') {
            openshift.withCluster() {
                openshift.withProject("${params.DEPLOY_PROJECT}") {
                    openshift.create(openshift.process(readFile('openshift/wordpress/svc_wp.yaml'),
                        "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}")) //wordpress service creation
                    println "service ${params.APPLICATION_NAME} created"

                    openshift.create(openshift.process(readFile("openshift/wordpress/route_wp.yaml"),
                        "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}")) //wordpress route creation
                    println "route ${params.APPLICATION_NAME} created"

                    openshift.create(openshift.process(readFile("openshift/wordpress/configmaps/wordpress-init-mysql-db.yaml"),
                        "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}")) //create configmap to populate database with the sql dump before wordpress application get started
                        println " configmaps init-mysql-db created"

                    //define route1 as route object to retrieve the hostname    
                    def route1 = openshift.selector("route", "${params.APPLICATION_NAME}").narrow('route').object() 
                    openshift.create(openshift.process(readFile('openshift/wordpress/DeploymentConfig_wp.yaml'),
                        "--param", "APPLICATION_NAME=${params.APPLICATION_NAME}", //Creation of the wordpress application
                        "--param", "ZIP_LINK_SQL=${params.ZIP_LINK_SQL}",
                        "--param", "OLD_URL=${params.OLD_URL}",
                        "--param", "DB_TABLES_PREFIX=${params.DB_TABLES_PREFIX}",                                                       
                        "--param", "NEW_URL=${route1.spec.host}")) //put route1 hostname as new url
                    println "deploymentconfig ${params.APPLICATION_NAME} created"

                    echo "waiting for wordpress to become running"
                    openshift.selector("dc", "${params.APPLICATION_NAME}").related('pods').untilEach(1) {
                        def dc = it.object()
                        shortname = dc.metadata.name
                        echo "This is the wordpress pod name: $shortname"
                        return (dc.status.phase == "Running")
                    }
                }
            }

        } // stage
        stage('download site content') {
            openshift.withCluster() {
                openshift.withProject() {
                    echo "$shortname"
                    //send wget command to dowload the zip file into the wordpress container
                    echo openshift.rsh("${shortname}", "wget -O wordpress.zip --timeout=60 --retry-connrefused ${params.ZIP_LINK_WP}").out //download wordpress zip site
                    println "The site archive have been downloaded at ${shortname} container"
                    
                }
            }

        } // stage
        stage('unzip site content') {
            openshift.withCluster() {
                openshift.withProject() {
                    echo "$shortname"
                    //unzip the zip file
                    echo openshift.rsh("${shortname}", "unzip wordpress.zip -d wordpress").out
                    println "The site archive have been unziped at ${shortname} container"  
                }
            }
        } // stage

        //Configuration ofthe wp-config.php file
        stage('wp-config Configuration') {
            openshift.withCluster() {
                openshift.withProject() {
                    echo "$shortname"
                     //define route1 as route object to retrieve the hostname and use it as WP_HOME and WP_SITEURL
                    def route1 = openshift.selector("route", "${params.APPLICATION_NAME}").narrow('route').object()
                    echo openshift.rsh("${shortname}", "mv wp-config.php wp-config.new").out
                    echo openshift.rsh("${shortname}", "sh -c 'echo \"define( \\\' WP_HOME \\\' , \\\' https://${route1.spec.host} \\\' );\" >> wp-config.new'").out
                    echo openshift.rsh("${shortname}", "sh -c 'echo \"define( \\\' WP_SITEURL \\\' , \\\' https://${route1.spec.host} \\\' );\" >> wp-config.new'").out
                    echo openshift.rsh("${shortname}", "sh -c 'echo \"define(\\\'FORCE_SSL_ADMIN\\\', true);\" >> wp-config.new'").out
                    echo openshift.rsh("${shortname}", "sh -c 'echo \"if (strpos(\\\$_SERVER[\\\'HTTP_X_FORWARDED_PROTO\\\'], \\\'https\\\') !== false) \n \\\$_SERVER[\\\'HTTPS\\\']=\\\'on\\\';\" >> wp-config.new'").out
                    println "wp-config.php was renamed to wp-config.new at ${shortname} container"
                }
            }
        } // stage
        
        //import site backup
       stage('copy site content') {
            openshift.withCluster() {
                openshift.withProject() {
                    echo "$shortname"
                    echo openshift.rsh("${shortname}", "cd /opt/app-root/src/").out
                    echo openshift.rsh("${shortname}", "sh -c 'cp -rv /opt/app-root/src/wordpress/* /opt/app-root/src/'").out
                    echo openshift.rsh("${shortname}", "sh -c 'cp -fv /opt/app-root/src/wp-config.new /opt/app-root/src/wp-config.php'").out
                    println "The site content have copied at ${shortname} container" 
                }
            }
        } // stage
        // copy the site .htaccess
        stage('copy htaccess') {
            openshift.withCluster() {
                openshift.withProject() {
                    echo "$shortname"
                    echo openshift.rsh("${shortname}", "cp -v /opt/app-root/src/wordpress/.htaccess /opt/app-root/src/").out
                    println "The site content have copied  at ${shortname} container"
                }
            }
        } // stage

    } //timeout
} //node
