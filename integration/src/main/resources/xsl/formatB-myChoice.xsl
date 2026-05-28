<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
  <xsl:template match="myChoiceSet">
    <classes>
      <xsl:for-each select="课程">
        <class origin="B">
          <id><xsl:value-of select="编号"/></id>
          <name><xsl:value-of select="名称"/></name>
          <time><xsl:value-of select="课时"/></time>
          <score><xsl:value-of select="学分"/></score>
          <teacher><xsl:value-of select="老师"/></teacher>
          <location><xsl:value-of select="地点"/></location>
          <share>Y</share>
          <sno><xsl:value-of select="学号"/></sno>
          <grade><xsl:value-of select="得分"/></grade>
        </class>
      </xsl:for-each>
    </classes>
  </xsl:template>
</xsl:stylesheet>
