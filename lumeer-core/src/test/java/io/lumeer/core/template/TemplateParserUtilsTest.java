package io.lumeer.core.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class TemplateParserUtilsTest {

   public static final String XML = "      <value name=\"DOCUMENT\">\n              <block type=\"variables_get_5cbb90e2857aba0090d78dbc_document\" id=\"y7iJEiiD^sd0KHU@:UV?\" editable=\"false\">\n                <field name=\"VAR\" id=\"X@fvQjT{Q8(k+CBPJUg-\" variabletype=\"5cbb90e2857aba0090d78dbc_document\">newDocument</field>\n              </block>\n            </value>\n            <value"
         + " name=\"VALUE\">\n              <block type=\"text\" id=\"?h4Wa~eFf(2N[fcM5fyx\">\n                <field name=\"TEXT\">#93c47d</field>\n              </block>\n            </value>\n          </block>\n        </statement>\n        <statement name=\"ELSE\">\n          <block type=\"controls_ifelse\" id=\"5kZhE`J{^Kc7G^|teWbc\">\n            <value name=\"IF0\">\n              "
         + "<block type=\"logic_compare\" id=\"=p{[jBShmOx4Fo8IQF6p\">\n                <field name=\"OP\">EQ</field>\n                <value name=\"A\">\n                  <block type=\"text\" id=\"4XT-dW5#MY^Nq-r@L:M[\">\n                    <field name=\"TEXT\">In Progress</field>\n                  </block>\n                </value>\n                <value name=\"B\">\n                  "
         + "<block type=\"get_attribute\" id=\"Aklmx6o7aU0RKd7?v%G]\">\n                    <field name=\"ATTR\">a8</field>\n                    <value name=\"DOCUMENT\">\n                      <block type=\"variables_get_5cbb90e2857aba0090d78dbc_document\" id=\"X-]?_OUxulHq4OVpxOro\" editable=\"false\">\n ";
   public static final String XML_UPPER = "      <value name=\"DOCUMENT\">\n              <block type=\"variables_get_5CBB90E2857ABA0090D78DBC_document\" id=\"y7iJEiiD^sd0KHU@:UV?\" editable=\"false\">\n                <field name=\"VAR\" id=\"X@fvQjT{Q8(k+CBPJUg-\" variabletype=\"5cbb90e2857aba0090d78dbc_document\">newDocument</field>\n              </block>\n            </value>\n            <value"
         + " name=\"VALUE\">\n              <block type=\"text\" id=\"?h4Wa~eFf(2N[fcM5fyx\">\n                <field name=\"TEXT\">#93c47d</field>\n              </block>\n            </value>\n          </block>\n        </statement>\n        <statement name=\"ELSE\">\n          <block type=\"controls_ifelse\" id=\"5kZhE`J{^Kc7G^|teWbc\">\n            <value name=\"IF0\">\n              "
         + "<block type=\"logic_compare\" id=\"=p{[jBShmOx4Fo8IQF6p\">\n                <field name=\"OP\">EQ</field>\n                <value name=\"A\">\n                  <block type=\"text\" id=\"4XT-dW5#MY^Nq-r@L:M[\">\n                    <field name=\"TEXT\">In Progress</field>\n                  </block>\n                </value>\n                <value name=\"B\">\n                  "
         + "<block type=\"get_attribute\" id=\"Aklmx6o7aU0RKd7?v%G]\">\n                    <field name=\"ATTR\">a8</field>\n                    <value name=\"DOCUMENT\">\n                      <block type=\"variables_get_5CBB90E2857ABA0090D78DBC_document\" id=\"X-]?_OUxulHq4OVpxOro\" editable=\"false\">\n ";

   @Test
   public void testReplacer() {
      var res = TemplateParserUtils.replacer(XML, "<block type=\"variables_get_", "_document", String::toUpperCase);
      assertThat(res).isEqualTo(XML_UPPER);
   }

}
