<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="myChoiceSet">
    <classes>
      <xsl:for-each select="课程">
        <class origin="A">
          <id><xsl:value-of select="课程编号"/></id>
          <name><xsl:value-of select="课程名称"/></name>
          <time><xsl:value-of select="课时"/></time>
          <score><xsl:value-of select="学分"/></score>
          <teacher><xsl:value-of select="授课老师"/></teacher>
          <location><xsl:value-of select="授课地点"/></location>
          <share>Y</share>
          <sno><xsl:value-of select="学生编号"/></sno>
          <grade><xsl:value-of select="成绩"/></grade>
        </class>
      </xsl:for-each>
    </classes>
  </xsl:template>
</xsl:stylesheet>
