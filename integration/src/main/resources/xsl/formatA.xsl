<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="课程集">
    <classes>
      <xsl:for-each select="课程">
        <class>
          <id><xsl:value-of select="课程编号"/></id>
          <name><xsl:value-of select="课程名称"/></name>
          <time><xsl:value-of select="课时"/></time>
          <score><xsl:value-of select="学分"/></score>
          <teacher><xsl:value-of select="授课老师"/></teacher>
          <location><xsl:value-of select="授课地点"/></location>
          <share><xsl:value-of select="共享"/></share>
          <origin>A</origin>
        </class>
      </xsl:for-each>
    </classes>
  </xsl:template>
</xsl:stylesheet>
