<%
/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.PollingUtil" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.search.umls.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.collections.MultiValueMap" %>
<%@ page import="org.labkey.api.search.SearchUrls" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.springframework.web.servlet.ModelAndView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public static class StringSet extends MultiValueMap<String,String>
    {
        StringSet()
        {
            super(new TreeMap<String,Collection<String>>());
        }
        @Override
        protected Collection createValueCollection()
        {
            return new TreeSet<String>();
        }
    }
    // sort by ISPREF
    public static class ISPREFComparator implements Comparator<ConceptName>
    {
        public int compare(ConceptName o1, ConceptName o2)
        {
            if (o1.ISPREF.equals(o2.ISPREF))
                return 0;
            if ("Y".equals(o1.ISPREF)) return -1;
            if ("Y".equals(o2.ISPREF)) return 1;
            return o1.ISPREF.compareTo(o2.ISPREF);
        }
        public boolean equals(Object obj)
        {
            return obj.getClass() == this.getClass();
        }
    }
    
%>
<%
JspView<UmlsController.ConceptForm> me = (JspView<UmlsController.ConceptForm>) HttpView.currentView();
UmlsController.ConceptForm form = me.getModelBean();
String CUI = StringUtils.trimToNull(form.getCUI());

Set<String> preferredSAB = PageFlowUtil.set("SNOMEDCT","LCN","MSH","NCI");
%>
<form action="concept.view" method=GET><input name=CUI value="<%=h(CUI)%>"><input type="submit" value="FIND"></form>
<%
if (null != CUI)
{
    DbSchema umls = DbSchema.get("umls");
    ConceptName[] names = UmlsController.getNames(umls,CUI);
    if (names.length == 0)
    {
        %>Searching for '<%=h(CUI)%>'<!--<%
        out.flush();
        throw new RedirectException(urlProvider(SearchUrls.class).getSearchURL(CUI, "umls"));
    }
    Related[] related = UmlsController.getRelated(umls,CUI);
    Definition[] definitions = UmlsController.getDefinitions(umls,CUI);
    SemanticType[] types = UmlsController.getSemanticType(umls,CUI);


//    Arrays.sort(names,new ISPREFComparator());
    StringSet prefNames = new StringSet();
    StringSet sabNames = new StringSet();
    StringSet otherNames = new StringSet();
    for (ConceptName n : names)
    {
        if ("Y".equals(n.ISPREF))
            prefNames.put(n.STR,n.SAB);
        else if (preferredSAB.contains(n.SAB))
           sabNames.put(n.STR,n.SAB);
        else
            otherNames.put(n.STR,n.SAB);
    }
    if (prefNames.size() == 0)
        prefNames = sabNames.size()==0 ? otherNames : sabNames;
    %><table><%
    for (String name : prefNames.keySet())
    {
        Set<String> sabs = (Set<String>)prefNames.get(name);
        %><tr><td><%=h(name)%></td><td><%
        for (String sab : sabs)
        {
            %><%=h(sab)%>&nbsp;<%
        }
        %></td></tr><%
    }
    %></table><%
    
    for (Definition d : definitions)
    {
        %><%=h(d.SAB)%>:&nbsp;<%=h(d.DEF)%><br><%
    }
    for (SemanticType t : types)
    {
        %><%=h(t.toString())%><br><%
    }
    Set<String> relatedCUI = new HashSet<String>();
    Set<String> parents = new HashSet<String>();
    Set<String> children = new HashSet<String>();
    Set<String> siblings = new HashSet<String>();
    ArrayList<Related> remaining = new ArrayList<Related>();
    Set<Relationship> other = new TreeSet<Relationship>();
    for (Related r : related)
    {
        try
        {
            relatedCUI.add(r.CUI1);
            relatedCUI.add(r.CUI2);
            Relationship rel = Relationship.valueOf(r.REL);
            if (CUI.equals(r.CUI2) && null != rel.inverse)
            {
                rel = rel.inverse;
                String t = r.CUI1; r.CUI1=r.CUI2; r.CUI2=t;
            }
            if (CUI.equals(r.CUI1))
            {
                if (rel == Relationship.RB || rel == Relationship.PAR)
                {
                    parents.add(r.CUI2);
                }
                else if (rel == Relationship.RN || rel == Relationship.CHD)
                {
                    children.add(r.CUI2);
                }
                else if (rel == Relationship.SIB)
                {
                    siblings.add(r.CUI2);
                }
                else if (rel == Relationship.SY && CUI.equals(r.CUI2))
                {
                    continue;
                }
                else
                {
                    remaining.add(r);
                    other.add(rel);
                }
            }
        }
        catch (IllegalArgumentException x)
        {
        }
    }
    relatedCUI.remove(CUI);
    Map<String,String> cuiNames = UmlsController.getNames(umls, relatedCUI);
    %><b>parents</b><br><%
    for (String c : parents)
    {
        %><a href="?CUI=<%=c%>"><%=c%></a> <%=cuiNames.get(c)%><br><%
    }
    %><b>children</b><br><%
    for (String c : children)
    {
        %><a href="?CUI=<%=c%>"><%=c%></a> <%=cuiNames.get(c)%><br><%
    }
    %><b>siblings</b><br><%
    for (String c : siblings)
    {
        %><a href="?CUI=<%=c%>"><%=c%></a> <%=cuiNames.get(c)%><br><%
    }
    %><b>other</b><br><%
    for (Related r : remaining)
    {
        if (CUI.equals(r.CUI1))
        {
            %><%=r.REL%> <a href="?CUI=<%=r.CUI2%>"><%=r.CUI2%></a> <%=cuiNames.get(r.CUI2)%><br><%
        }
        else
        {
            %><a href="?CUI=<%=r.CUI1%>"><%=r.CUI1%></a> <%=r.REL%> <%=cuiNames.get(r.CUI1)%><br><%
        }
    }
    %><br><%
    for (Relationship rel : other)
    {
        %><%=rel.name()%>&nbsp;<%=rel.description%><br><%
    }
%>
<%
    }
%>
