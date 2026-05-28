<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>

  <xsl:template match="/">
    <choices>
      <xsl:apply-templates select="选课集/选课 | choices/choice"/>
    </choices>
  </xsl:template>

  <xsl:template match="选课">
    <choice>
      <sid><xsl:value-of select="学生编号 | 学号"/></sid>
      <cid><xsl:value-of select="课程编号"/></cid>
      <xsl:if test="string-length(成绩 | 得分) &gt; 0">
        <score><xsl:value-of select="成绩 | 得分"/></score>
      </xsl:if>
      <originStudent>
        <xsl:call-template name="student-origin">
          <xsl:with-param name="sid" select="学生编号 | 学号"/>
        </xsl:call-template>
      </originStudent>
      <originCourse>
        <xsl:call-template name="course-origin">
          <xsl:with-param name="cid" select="课程编号"/>
        </xsl:call-template>
      </originCourse>
    </choice>
  </xsl:template>

  <xsl:template match="choice">
    <choice>
      <sid><xsl:value-of select="Sno"/></sid>
      <cid><xsl:value-of select="Cno"/></cid>
      <xsl:if test="string-length(Grd) &gt; 0">
        <score><xsl:value-of select="Grd"/></score>
      </xsl:if>
      <originStudent>
        <xsl:call-template name="student-origin">
          <xsl:with-param name="sid" select="Sno"/>
        </xsl:call-template>
      </originStudent>
      <originCourse>
        <xsl:call-template name="course-origin">
          <xsl:with-param name="cid" select="Cno"/>
        </xsl:call-template>
      </originCourse>
    </choice>
  </xsl:template>

  <xsl:template name="student-origin">
    <xsl:param name="sid"/>
    <xsl:choose>
      <xsl:when test="starts-with($sid, 'AS')">A</xsl:when>
      <xsl:when test="starts-with($sid, 'BS')">B</xsl:when>
      <xsl:otherwise>C</xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="course-origin">
    <xsl:param name="cid"/>
    <xsl:choose>
      <xsl:when test="starts-with($cid, 'AC')">A</xsl:when>
      <xsl:when test="starts-with($cid, 'BC')">B</xsl:when>
      <xsl:otherwise>C</xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
