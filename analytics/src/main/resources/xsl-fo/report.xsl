<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:fo="http://www.w3.org/1999/XSL/Format">
  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>

  <xsl:template match="analyticsReport">
    <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
      <fo:layout-master-set>
        <fo:simple-page-master master-name="A4" page-width="210mm" page-height="297mm"
            margin-top="20mm" margin-bottom="20mm" margin-left="20mm" margin-right="20mm">
          <fo:region-body/>
        </fo:simple-page-master>
      </fo:layout-master-set>

      <fo:page-sequence master-reference="A4">
        <fo:flow flow-name="xsl-region-body">
          <fo:block font-size="18pt" font-weight="bold" text-align="center"
              space-after="10mm">集成教务系统分析报告</fo:block>
          <fo:block font-size="10pt" text-align="center"
              space-after="15mm">生成时间：<xsl:value-of select="@timestamp"/></fo:block>

          <!-- Summary -->
          <fo:block font-size="14pt" font-weight="bold" space-after="5mm">系统概览</fo:block>
          <fo:table table-layout="fixed" width="100%" border="1pt solid black">
            <fo:table-body>
              <fo:table-row>
                <fo:table-cell padding="3pt"><fo:block>总学生数</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="summary/totalStudents"/></fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>总课程数</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="summary/totalCourses"/></fo:block></fo:table-cell>
              </fo:table-row>
              <fo:table-row>
                <fo:table-cell padding="3pt"><fo:block>共享课程</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="summary/totalShared"/></fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>跨院选课</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="summary/totalCross"/></fo:block></fo:table-cell>
              </fo:table-row>
            </fo:table-body>
          </fo:table>

          <!-- College detail -->
          <fo:block font-size="14pt" font-weight="bold" space-before="15mm" space-after="5mm">各院明细</fo:block>
          <fo:table table-layout="fixed" width="100%" border="1pt solid black">
            <fo:table-header>
              <fo:table-row font-weight="bold">
                <fo:table-cell padding="3pt"><fo:block>学院</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>学生数</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>课程数</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>共享课程</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>跨院选课</fo:block></fo:table-cell>
              </fo:table-row>
            </fo:table-header>
            <fo:table-body>
              <xsl:choose>
                <xsl:when test="colleges/college">
                  <xsl:for-each select="colleges/college">
                    <fo:table-row>
                      <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="@code"/></fo:block></fo:table-cell>
                      <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="students"/></fo:block></fo:table-cell>
                      <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="courses"/></fo:block></fo:table-cell>
                      <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="shared"/></fo:block></fo:table-cell>
                      <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="cross"/></fo:block></fo:table-cell>
                    </fo:table-row>
                  </xsl:for-each>
                </xsl:when>
                <xsl:otherwise>
                  <fo:table-row>
                    <fo:table-cell number-columns-spanned="5" padding="3pt"><fo:block>无数据</fo:block></fo:table-cell>
                  </fo:table-row>
                </xsl:otherwise>
              </xsl:choose>
            </fo:table-body>
          </fo:table>

          <!-- Top Courses -->
          <fo:block font-size="14pt" font-weight="bold" space-before="15mm" space-after="5mm">课程热度 Top 10</fo:block>
          <fo:table table-layout="fixed" width="100%" border="1pt solid black">
            <fo:table-header>
              <fo:table-row font-weight="bold">
                <fo:table-cell padding="3pt"><fo:block>排名</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>课程名</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>选课人数</fo:block></fo:table-cell>
                <fo:table-cell padding="3pt"><fo:block>来源</fo:block></fo:table-cell>
              </fo:table-row>
            </fo:table-header>
            <fo:table-body>
              <xsl:choose>
                <xsl:when test="topCourses/course">
                  <xsl:for-each select="topCourses/course">
                    <fo:table-row>
                      <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="position()"/></fo:block></fo:table-cell>
                      <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="@name"/></fo:block></fo:table-cell>
                      <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="@enrollments"/></fo:block></fo:table-cell>
                      <fo:table-cell padding="3pt"><fo:block><xsl:value-of select="@origin"/></fo:block></fo:table-cell>
                    </fo:table-row>
                  </xsl:for-each>
                </xsl:when>
                <xsl:otherwise>
                  <fo:table-row>
                    <fo:table-cell number-columns-spanned="4" padding="3pt"><fo:block>无数据</fo:block></fo:table-cell>
                  </fo:table-row>
                </xsl:otherwise>
              </xsl:choose>
            </fo:table-body>
          </fo:table>
        </fo:flow>
      </fo:page-sequence>
    </fo:root>
  </xsl:template>
</xsl:stylesheet>
