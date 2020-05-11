# Deploy WordPress website on SCALe from an sql dump and website content files

To migrate a WordPress website on SCALe, you need to have at least:

* A zip file containing all the needed files, uploads and plugins for WordPress.
* A zip containing SQL dump from your MySQL database.

## Kind Reminder

This is a technical Knowledge Base article describing a WP site relocation method that does not take into account any specific configuration a production website may need. In particular it does not cover:
* Specific Wordpress and/or database version requirements
* Extra plugins setup and configuration, especially those requiring callback validation from WP
* DNS,  Secure https (certificates) or users authentication
* Other technical dependencies such as email gateways

#### Deploy Jenkins

> NB 
>pre-requisites:

>If you decide to fork this project on a private repository, you must have an account which does have read access on your new repository and create a secret that you can name jenkins-git-credential 

Access the OpenShift Console and then Browse Catalog.
* Select the CI/CD tab then select Jenkins (Persistent).
* In the opened browser windows, select and then fill the installation template with the correct value especially the Project Name, the Memory Limit and Volume Capacity for data storage then click next.
    * Memory limit: 1Gi
    * Volume Capacity: 1Gi
* Keep Do not bind at this time option checked and then click Create.

After a few minutes, your Jenkins is up and running and can be accessed via the following default route: https://jenkins-wordpress-migration-test.scale-eu.sanofi.com/
Give authorizations to jenkins user to deploy on wordpress-migration-test project
```
oc policy add-role-to-user edit system:serviceaccount:wordpress-migration-test:jenkins --namespace=wordpress-migration-test
```


## Build/Deploy pipelines

The folder pipelines contains the pipeline definitions:

- The build-pipeline is used to create the Wordpress image 
- The deploy-pipeline is used to deploy the application and all the tools in an openshift project.

### Build-pipeline

This pipeline prepares the build environment and builds the application images.   

This pipeline has the following stages:   
* Git clone project: deployment templates to be used to create the imagestreams and the buildconfigs are extracted from https://git-scale-tools.scale-n-eu.sanofi.com/I0425375/WordPress-Migration.git
* Create imagestreams: create the imagestream needed by the build process 
* Create and run the buildconfigs: create the buildconfigs and run it to build all the images 


#### Build-pipeline Installation 

##### Install using the OpenShift GUI:

* Go to the repository where you can find the  yaml file for the build pipeline yaml: https://git-scale-tools.scale-n-eu.sanofi.com/I0425375/WordPress-Migration
* Copy the yaml from [pipelines/wordpress-build-pipeline.yaml](https://git-scale-tools.scale-n-eu.sanofi.com/I0425375/WordPress-Migration/src/master/pipeline/wordpress-build-pipeline.yaml)
* Access the OpenShift Console and then click on the top right on Add to Project and then import YAML/JSON.
* Paste the Yaml and then click on create.
* Fill in with the required information.

#### Running Build-pipeline

##### Run using the oc command line:

```
oc start-build wordpress-build-pipeline
```

##### Run using the OpenShift GUI:

Using your wordpress-migration-test project access the OpenShift Console.
* Click on Builds and then choose pipelines.
* In the opened window, choose wordpress-build-pipeline and then Click on start-pipeline

After Running the pipeline, you should have at the end an image:

* your_application_name-img

### Deploy-pipeline

This pipeline is used to deploy (restore) your full wordpress site from scratch in a few minutes.

It must be executed in the mordpress-migration-test project.

The full deployment has the following stages:

* Git clone project: deployment templates and parameters to be used to deploy each component are extracted from https://git-repository.scale-eu.sanofi.com/I0425375/WordPress-Migration.git
* Clean up existing environment: remove all the existing components except persistent storage
* Create PVC if not exists
* Create secrets: deletes if exists, then creates the secrets for mysql database
* Create Mysql: Deploy the MySQL database by creating first the service and processing the deploymentconfig using the specific environment parameters.
* Create Wordpress: Deploy the Wordpress by creating first the service, exposing it to an external route, create the config for init container script and then processing the deploymentconfig using the specific environment parameters.

#### Deploy-pipeline Installation 

##### Install using the oc command line:

```
git clone https://git-repository.scale-eu.sanofi.com/I0425375/WordPress-Migration.git
cd Wordpress-Migration
oc project mordpress-migration-test
oc create -f pipelines/wordpress-deploy-pipeline.yaml
```

##### Install using the OpenShift GUI:

* Go to the repository where you can find the deploy pipeline yaml: https://git-scale-tools.scale-n-eu.sanofi.com/I0425375/WordPress-Migration
* Copy the yaml from and [pipelines/wordpress-deploy-pipeline.yaml](https://git-scale-tools.scale-n-eu.sanofi.com/I0425375/WordPress-Migration/src/master/pipeline/wordpress-deploy-pipeline.yaml)
* Access the OpenShift Console and then click on the top right on Add to Project and then import YAML/JSON.
* Paste the Yaml and then click on create.

#### Running Deploy-pipeline

##### Run using the oc command line:

```
oc start-build wordpress-deploy-pipeline
```

##### Run using the OpenShift GUI:

Using your wordpress-migration-test project access the OpenShift Console.
* Click on Builds and then choose pipelines.
* In the opened window, choose wordpress-deploy-pipeline and then Click on start-pipeline

The pipeline will use the file params/dev/deploy.params to deploy the environment. This file must contain all the parameters needed to deploy the environment.

## Openshift resources

The folder openshift contains the openshift resources definitions

## Build and Deploy Parameter

The folder params contains two files [build.params](https://git-scale-tools.scale-n-eu.sanofi.com/I0425375/WordPress-Migration/src/master/params/build.params) and [deploy.params](https://git-scale-tools.scale-n-eu.sanofi.com/I0425375/WordPress-Migration/src/master/params/deploy.params)

## Jenkins Pipeline

The folder pipeline contains the [build-pipeline](https://git-scale-tools.scale-n-eu.sanofi.com/I0425375/WordPress-Migration/src/master/pipeline/wordpress-build-pipeline.groovy) and the [deploy-pipeline](https://git-scale-tools.scale-n-eu.sanofi.com/I0425375/WordPress-Migration/src/master/pipeline/wordpress-deploy-pipeline.groovy)