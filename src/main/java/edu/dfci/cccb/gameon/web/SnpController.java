package edu.dfci.cccb.gameon.web;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.roo.addon.web.mvc.controller.scaffold.RooWebScaffold;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import ch.lambdaj.Lambda;
import edu.dfci.cccb.gameon.domain.QueryLimitExceededException;
import edu.dfci.cccb.gameon.domain.Snp;
import edu.dfci.cccb.gameon.util.SnpsFilterUtil;

@RequestMapping ("/snps")
@Controller
@RooWebScaffold (path = "snps", formBackingObject = Snp.class)
public class SnpController {

  private static final Logger log = Logger.getLogger (SnpController.class);

  private static final int PAGE_SIZE = 100;

  private static final int BUFFER_SIZE = 4096;

  @RequestMapping (params = "find=generic", method = RequestMethod.GET)
  public String findSnpsGeneric (WebRequest request, Model model) throws QueryLimitExceededException {
    Map<String, String[]> searchTerms = new HashMap<String, String[]> ();
    searchTerms.putAll (request.getParameterMap ());
    searchTerms.remove ("find");
    searchTerms.remove ("page");
    searchTerms.remove ("size");
    for (Iterator<String[]> iterator = searchTerms.values ().iterator (); iterator.hasNext ();)
      if (isEmpty (iterator.next ()))
        iterator.remove ();
    formatSearchTerms (searchTerms);
    log.debug ("search terms are " + searchTerms);
    model.addAttribute ("snps",
                        Snp.findGeneric (searchTerms).setFirstResult (0)
                           .setMaxResults (PAGE_SIZE).getResultList ());
    model.addAttribute ("builds", SnpsFilterUtil.getBuilds ());
    model.addAttribute ("strands", SnpsFilterUtil.getStrands ());
    model.addAttribute ("nstudies", SnpsFilterUtil.getNStudies ());
    model.addAttribute ("effectAlleles", SnpsFilterUtil.getEffectAllele ());
    model.addAttribute ("refAlleles", SnpsFilterUtil.getRefAllele ());
    model.addAttribute ("chromosomes", SnpsFilterUtil.getChromosomes ());
    return "snps/list";
  }

  private static boolean isEmpty (String[] array) {
    if (array == null)
      return true;
    else
      for (int i = array.length; --i >= 0;)
        if (!"".equals (array[i]))
          return false;
    return true;
  }

  @RequestMapping (params = "download=generic", method = RequestMethod.GET)
  public void downloadSnpsGeneric (WebRequest request, HttpServletResponse response) throws QueryLimitExceededException {
    Map<String, String[]> searchTerms = new HashMap<String, String[]> ();
    searchTerms.putAll (request.getParameterMap ());
    searchTerms.remove ("download");
    searchTerms.remove ("page");
    searchTerms.remove ("size");
    for (Iterator<String[]> iterator = searchTerms.values ().iterator (); iterator
                                                                                  .hasNext ();)
      if (isEmpty (iterator.next ()))
        iterator.remove ();
    response.setContentType ("application/octet-stream");
    response.setHeader ("Content-Disposition",
                        "attachment;filename=data.txt");
    formatSearchTerms (searchTerms);
    InputStream rows = snpsToInputStream (Snp.findGeneric (searchTerms), (int) Snp.countGeneric (searchTerms));
    try {
      for (int c; -1 != (c = rows.read ());)
        response.getOutputStream ().write (c);
    } catch (IOException e) {
      log.warn ("IO exception during download for search terms "
                + searchTerms, e);
    }
  }

  @ExceptionHandler (QueryLimitExceededException.class)
  @ResponseStatus (value = HttpStatus.TOO_MANY_REQUESTS,
                   reason = "Maximum allowable query count exceeded, please try again later")
  public void handleQueryLimit () {}

  private static Map<String, String[]> formatSearchTerms (
                                                          Map<String, String[]> terms) {
    // marker name processing
    {
      List<String> result = new ArrayList<String> ();
      String[] markerNameTerm = terms.get ("markerName");
      if (markerNameTerm != null) {
        for (String markerNameArg : terms.get ("markerName"))
          for (String markerName : markerNameArg.split ("[ ,]+"))
            result.add (markerName + "%");
        terms.put ("markerName", result.toArray (new String[0]));
      }
    }
    return terms;
  }

  private static InputStream snpsToInputStream (final TypedQuery<Snp> query, final int count) {
    return new BufferedInputStream (new InputStream () {

      private Iterator<Snp> page = null;
      private static final int PAGE_SIZE = 200;
      private int currentIndex = 0;
      private ByteArrayInputStream current = new ByteArrayInputStream ((Lambda.join (Arrays.asList ("MarkerName",
                                                                                                    "RsNumber",
                                                                                                    "Chromosome",
                                                                                                    "Coordinate",
                                                                                                    "Build",
                                                                                                    "EffectAllele",
                                                                                                    "RefAllele",
                                                                                                    "EafUK",
                                                                                                    "Beta",
                                                                                                    "Se",
                                                                                                    "Or",
                                                                                                    "LCi",
                                                                                                    "UCi",
                                                                                                    "PValue"),
                                                                                     "\t") + "\n").getBytes ());

      @Override
      public int read () throws IOException {
        while (current.available () < 1)
          if (page != null && page.hasNext ())
            current = render (page.next ());
          else if (currentIndex < count) {
            page = query.setFirstResult (Math.max (currentIndex += PAGE_SIZE, count))
                        .setMaxResults (PAGE_SIZE)
                        .getResultList ()
                        .iterator ();
            continue;
          } else
            return -1;
        return current.read ();
      }

      @SuppressWarnings ("unchecked")
      private ByteArrayInputStream render (Snp row) {
        return new ByteArrayInputStream ((Lambda.join (Arrays.asList (row.getMarkerName (), row.getRsNumber (),
                                                                      row.getChromosome (), row.getCoordinate (),
                                                                      row.getBuild (), row.getEffectAllele (),
                                                                      row.getRefAllele (), row.getEafUkValue (),
                                                                      row.getBetavalue (), row.getSeValue (),
                                                                      row.getOrValue (), row.getLCiValue (),
                                                                      row.getUCiValue (), row.getPValue ()), "\t") + "\n").getBytes ());
      }
    },
                                    BUFFER_SIZE);
  }
}