import spock.lang.Specification

import static org.jfrog.artifactory.client.ArtifactoryClient.create

class SecurityReplicationTest extends Specification {
  def 'put and get security replication list test'() {
    setup:
    def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
    def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
    def conn = null

    when:
    conn = new URL("${baseurl}/securityReplication").openConnection()
    conn.requestMethod = 'PUT'
    conn.doOutput = true
    conn.setRequestProperty('Authorization', auth)
    def textFile = '{"securityReplication":{"urls":'
    textFile += '["http://localhost:8088/artifactory",'
    textFile += '"http://localhost:8081/artifactory",'
    textFile += '"http://localhost:8082/artifactory"],'
    textFile += '"filter":3,"whoami":"http://localhost:8088/artifactory",'
    textFile += '"authorization":{"username":"admin","password":"password"}}}'
    conn.outputStream.write(textFile.bytes)
    assert conn.responseCode == 200
    conn.disconnect()

    conn = new URL("${baseurl}/secRepJson").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    def reader = new InputStreamReader(conn.inputStream)
    def textFile2 = reader.text
    conn.disconnect()

    then:
    textFile == textFile2
  }

  def 'update extract diff patch test'() {
    setup:
    def baseurl = 'http://localhost:8088/artifactory/api/plugins/execute'
    def auth = "Basic ${'admin:password'.bytes.encodeBase64()}"
    def conn = new URL("$baseurl/testSecurityDump").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    def original = conn.inputStream.text, file = null, snapshot = null
    conn.disconnect()

    when:
    conn = new URL("$baseurl/testDBUpdate").openConnection()
    conn.requestMethod = 'POST'
    conn.doOutput = true
    conn.setRequestProperty('Authorization', auth)
    conn.setRequestProperty('Content-Type', 'application/json')
    file = new File("./src/test/groovy/SecurityReplicationTest/sec1.json")
    conn.outputStream << file.text
    assert conn.responseCode == 200
    conn.disconnect()

    conn = new URL("$baseurl/testSecurityDump").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    snapshot = conn.inputStream.text
    conn.disconnect()

    then:
    file.text == snapshot

    when:
    conn = new URL("$baseurl/testDBUpdate").openConnection()
    conn.requestMethod = 'POST'
    conn.doOutput = true
    conn.setRequestProperty('Authorization', auth)
    conn.setRequestProperty('Content-Type', 'application/json')
    file = new File("./src/test/groovy/SecurityReplicationTest/sec2.json")
    conn.outputStream << file.text
    assert conn.responseCode == 200
    conn.disconnect()

    conn = new URL("$baseurl/testSecurityDump").openConnection()
    conn.requestMethod = 'GET'
    conn.setRequestProperty('Authorization', auth)
    assert conn.responseCode == 200
    snapshot = conn.inputStream.text
    conn.disconnect()

    then:
    file.text == snapshot

    cleanup:
    conn = new URL("$baseurl/testDBUpdate").openConnection()
    conn.requestMethod = 'POST'
    conn.doOutput = true
    conn.setRequestProperty('Authorization', auth)
    conn.setRequestProperty('Content-Type', 'application/json')
    conn.outputStream << original
    assert conn.responseCode == 200
    conn.disconnect()
  }
}
