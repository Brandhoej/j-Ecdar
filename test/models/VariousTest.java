package models;

import Exceptions.CddAlreadyRunningException;
import Exceptions.CddNotRunningException;
import logic.Composition;
import logic.Refinement;
import logic.SimpleTransitionSystem;
import logic.TransitionSystem;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import parser.XMLParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class VariousTest {

    @After
    public void afterEachTest(){
        CDD.done();
    }

    @BeforeClass
    public static void setUpBeforeClass() {

    }

    @Test
    public void simple() throws CddAlreadyRunningException, CddNotRunningException {
        Automaton[] aut2 = XMLParser.parse("samples/xml/simple.xml", true);

        SimpleTransitionSystem simp2 = new SimpleTransitionSystem(aut2[0]);

        simp2.toXML("simple1.xml");

        assert (true);
    }

    @Test
    public void next() {
        Clock y = new Clock("y");
        List<Clock> clocks = new ArrayList<>(Arrays.asList(y));
        Zone z1 = new Zone(clocks.size()+1,true);
        z1.init();
        Zone z2 = new Zone(clocks.size()+1,true);
        z2.init();

        ClockGuard g1 = new ClockGuard(y, 5,  Relation.GREATER_EQUAL);
        z1.buildConstraintsForGuard(g1,clocks);

        z1.printDBM(true,true);
        ClockGuard g2 = new ClockGuard(y, 6,  Relation.GREATER_EQUAL);
        System.out.println(g2);
        z2.buildConstraintsForGuard(g2,clocks);
        z2.printDBM(true,true);

        List<Zone> zoneList1 = new ArrayList<>();
        List<Zone> zoneList2 = new ArrayList<>();
        zoneList1.add(z1);
        zoneList2.add(z2);
        Federation f1 = new Federation(zoneList1);
        Federation f2 = new Federation(zoneList2);

        System.out.println(f1.isSubset(f2));
        System.out.println(f2.isSubset(f1));
        System.out.println(f1.isSubset(f1));
        System.out.println(f2.isSubset(f2));
    }

    @Test
    public void testDiagonalConstraints() {
        Clock x = new Clock("x");
        Clock y = new Clock("y");

        ClockGuard g1 = new ClockGuard(x, 10, Relation.LESS_EQUAL);
        ClockGuard g2 = new ClockGuard(x, 5, Relation.GREATER_EQUAL);
        ClockGuard g3 = new ClockGuard(y, 3, Relation.LESS_EQUAL);
        ClockGuard g4 = new ClockGuard(y, 2, Relation.GREATER_EQUAL);


        List<Guard> inner = new ArrayList<>();
        inner.add(g1);
        inner.add(g2);
        inner.add(g3);
        inner.add(g4);


        List<Clock> clocks = new ArrayList<>();
        clocks.add(x);
        clocks.add(y);
        CDD.init(100,100,100);
        CDD.addClocks(clocks);

        CDD origin1 = new CDD(new AndGuard(inner));


        origin1 = origin1.delay();
        Guard origin1Guards = CDD.toGuardList(origin1,clocks);
        System.out.println(origin1Guards);
        assert(true);

    }


    @Test
    public void testClockReset() {
        Clock x = new Clock("x");
        Clock y = new Clock("y");

        ClockGuard g1 = new ClockGuard(x, 10, Relation.GREATER_EQUAL);
        ClockGuard g3 = new ClockGuard(y, 3, Relation.LESS_EQUAL);

        List<List<Guard>> guards1 = new ArrayList<>();
        List<Guard> inner = new ArrayList<>();
        inner.add(g1);
        inner.add(g3);
        guards1.add(inner);

        List<Clock> clocks = new ArrayList<>();
        clocks.add(x);
        clocks.add(y);
        CDD.init(100,100,100);
        CDD.addClocks(clocks);

        CDD origin1 = new CDD(new AndGuard(inner));

        Guard origin1Guards = CDD.toGuardList(origin1,clocks);
        System.out.println(origin1Guards);



        Update clockUpdate = new ClockUpdate(x,0);
        List<Update>  list1 = new ArrayList<>();
        list1.add(clockUpdate);
        origin1 = CDD.applyReset(origin1,list1);

        Guard origin2Guards = CDD.toGuardList(origin1,clocks);
        System.out.println(origin2Guards);

        assert(origin2Guards.toString().equals("((x==0 && y<=3 && y-x<=3 && x-y<=0))"));

    }

    @Test
    public void conversionTest()
    {
        int rawUpperBound = 43;
        int converted = rawUpperBound>>1;
        boolean included  =  (rawUpperBound & 1)==0 ? false : true;
        System.out.println(converted + " " + included);
    }

    @Test
    public void testCDDAllocateInterval() throws CddAlreadyRunningException, CddNotRunningException
    {
        CDD.init(100,100,100);
        Clock x = new Clock("x");
        Clock y = new Clock("y");
        List<Clock> clocks = new ArrayList<>();
        clocks.add(x);clocks.add(y);
        CDD.addClocks(clocks);
        CDD test = CDD.allocateInterval(1,0,2,false,3,false);
        System.out.println(CDD.toGuardList(test,clocks));
        test.printDot();
        assert(true);
    }

    @Test
    public void testCompOfCompRefinesSpec() throws CddAlreadyRunningException, CddNotRunningException {

        Automaton[] aut2 = XMLParser.parse("samples/xml/university-slice.xml", true);

        CDD.init(1000,1000,1000);
        List<Clock> clocks = new ArrayList<>();
        clocks.addAll(aut2[0].getClocks());
        clocks.addAll(aut2[1].getClocks());
        clocks.addAll(aut2[2].getClocks());
        clocks.addAll(aut2[3].getClocks());
        CDD.addClocks(clocks);

        SimpleTransitionSystem adm = new SimpleTransitionSystem((aut2[3]));
        SimpleTransitionSystem machine = new SimpleTransitionSystem((aut2[0]));
        SimpleTransitionSystem researcher = new SimpleTransitionSystem((aut2[1]));
        SimpleTransitionSystem spec = new SimpleTransitionSystem((aut2[2]));

        assertTrue(new Refinement(
                new Composition(new TransitionSystem[]{adm,
                        new Composition(new TransitionSystem[]{machine, researcher})}),
                spec).check()
        );




    }
}
