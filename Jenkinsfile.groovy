#!/usr/bin/env grooy

node {
  def APP_NAME = 'httpd-php'
  def IMAGE_BASE = "${GITLAB_INNERSOURCE_REGISTRY}/devops/images"
  def IMAGE_VERSION = null
  def SCM_VARS = [:]

  def FAILURE = null
  def FROM_IMAGE = "${IMAGE_BASE}/usgs/centos"
  def IMAGE_NAME = "usgs/${APP_NAME}"
  def LOCAL_IMAGE = "local/${APP_NAME}:latest"
  def OWASP_CONTAINER = "${APP_NAME}-${BUILD_ID}-OWASP"
  def OWASP_IMAGE = "${IMAGE_BASE}/owasp/zap2docker-stable"
  def PENTEST_CONTAINER = "${APP_NAME}-PENTEST"


  try {
    stage('Initialize') {
      cleanWs()

      SCM_VARS = checkout scm

      if (params.GIT_BRANCH != '') {
        sh "git checkout --detach ${params.GIT_BRANCH}"

        SCM_VARS.GIT_BRANCH = params.GIT_BRANCH
        SCM_VARS.GIT_COMMIT = sh(
          returnStdout: true,
          script: "git rev-parse HEAD"
        )
      }

      // Determine image tag to use
      if (SCM_VARS.GIT_BRANCH != 'origin/master') {
        IMAGE_VERSION = SCM_VARS.GIT_BRANCH.split('/').last().replace(' ', '_')
      } else {
        IMAGE_VERSION = 'latest'
      }
    }

    stage('Build') {
      ansiColor('xterm') {
        // Tag for internal registry
        sh """
          docker build \
            --build-arg FROM_IMAGE=${FROM_IMAGE} \
            -t ${LOCAL_IMAGE} .
        """
      }
    }

    stage('Scan') {
      def ZAP_API_PORT = '8090'
      def OWASP_REPORT_DIR = "${WORKSPACE}/owasp-data"


      // Ensure report output directory exists
      sh """
        if [ ! -d "${OWASP_REPORT_DIR}" ]; then
          mkdir -p ${OWASP_REPORT_DIR}
          chmod 777 ${OWASP_REPORT_DIR}
        fi
      """

      // Start a container to run penetration tests against
      sh """
        docker run --rm --name ${PENTEST_CONTAINER} \
          -d ${IMAGE_NAME}:${IMAGE_VERSION}
      """

      // Start a container to execute OWASP PENTEST
      sh """
        docker run --rm -d -u zap \
          --name=${OWASP_CONTAINER} \
          --link=${PENTEST_CONTAINER}:application \
          -v ${OWASP_REPORT_DIR}:/zap/reports:rw \
          -i ${OWASP_IMAGE} \
          zap.sh \
          -daemon \
          -port ${ZAP_API_PORT} \
          -config api.disablekey=true
      """

      // Wait for OWASP container to be ready, but not for too long
      timeout(
        time: 20,
        unit: 'SECONDS'
      ) {
        echo 'Waiting for OWASP container to finish starting up'
        sh """
          set +x
          status='FAILED'
          while [ \$status != 'SUCCESS' ]; do
            sleep 1;
            status=`\
              (\
                docker exec -i ${OWASP_CONTAINER} \
                  curl -I localhost:${ZAP_API_PORT} \
                  > /dev/null 2>&1 && echo 'SUCCESS'\
              ) \
              || \
              echo 'FAILED'\
            `
          done
        """
      }

      // Run the penetration tests
      ansiColor('xterm') {
        sh """
          PENTEST_IP='application'
          docker exec ${OWASP_CONTAINER} \
            zap-cli -v -p ${ZAP_API_PORT} spider \
            http://\$PENTEST_IP/
          docker exec ${OWASP_CONTAINER} \
            zap-cli -v -p ${ZAP_API_PORT} active-scan \
            http://\$PENTEST_IP/
          docker exec ${OWASP_CONTAINER} \
            zap-cli -v -p ${ZAP_API_PORT} report \
            -o /zap/reports/owasp-zap-report.html -f html
          docker stop ${OWASP_CONTAINER} ${PENTEST_CONTAINER}
        """
      }

      // Publish results
      publishHTML (target: [
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: OWASP_REPORT_DIR,
        reportFiles: 'owasp-zap-report.html',
        reportName: 'OWASP ZAP Report'
      ])
    }

    stage('Publish') {
      ansiColor('xterm') {
        // Create tags of local image for publication
        sh """
          # For internal registry
          docker tag \
            ${LOCAL_IMAGE} \
            ${IMAGE_BASE}/${IMAGE_NAME}:${IMAGE_VERSION}

          # For docker hub
          docker tag \
            ${LOCAL_IMAGE} \
            ${IMAGE_NAME}:${IMAGE_VERSION}
        """
      }

      docker.withRegistry(
        "https://${GITLAB_INNERSOURCE_REGISTRY}",
        'innersource-hazdev-cicd'
      ) {
        ansiColor('xterm') {
          sh "docker push ${IMAGE_BASE}/${IMAGE_NAME}:${IMAGE_VERSION}"
        }
      }

      docker.withRegistry('', 'usgs-docker-hub-credentials') {
        ansiColor('xterm') {
          sh "docker push ${IMAGE_NAME}:${IMAGE_VERSION}"
        }
      }
    }
  } catch (err) {
    try {
      echo 'Sending email...'
      // mail([
      //   to: 'gs-haz_dev_team_group@usgs.gov',
      //   from: 'noreply@jenkins',
      //   subject: "Jenkins Pipeline Failed: ${env.BUILD_TAG}",
      //   body: "Details: ${err}"
      // ])
    } catch (inner) {
      echo "An error occured while sending email. '${inner}'"
    }

    FAILURE = err
  } finally {
    stage('Cleanup') {
      sh """
        set +e
        # Cleaning up any leftover containers...
        docker container rm --force \
          ${OWASP_CONTAINER} \
          ${PENTEST_CONTAINER}
        # Cleaning up any leftover images...
        docker image rm --force \
          ${DEPLOY_IMAGE} \
          ${LOCAL_IMAGE}
        exit 0
      """

      if (FAILURE) {
        currentBuild.result = 'FAILURE'
        throw FAILURE
      }
    }
  }
}
