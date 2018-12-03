package test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Arrays.asList;
import java.util.Collections;
import java.util.List;
import static java.util.stream.Collectors.toList;
import model.Booking;
import model.DataAccess;
import model.DataAccessException;
import static test.DataStoreInit.PRICE_LIST;
import static test.DataStoreInit.SEAT_COUNT;

/**
 * A single-user (single-thread) test program for {@link DataAccess}.
 * <p>
 * <i>Note to {@code DataAccess} implementors: </i> <br>
 * This program guarantees that any {@link model.Booking} object passed to the
 * {@link model.DataAccess#cancelBookings(List)} method was previously returned
 * by the {@link model.DataAccess#bookSeats(String, List, boolean)} or the
 * {@link model.DataAccess#bookSeats(String, List)} methods: booking IDs (if
 * any) generated by the {@code bookSeats} methods will be passed back to the
 * {@code cancelBookings} method without modification.
 *
 * @author Jean-Michel Busca
 *
 */
public class SingleUserTest {

  //
  // CLASS FIELDS
  //
  private static final TestStats test = new TestStats();

  //
  // MAIN
  //
  /**
   * Runs the program.
   *
   * @param args the command line arguments: URL, login, password
   *
   * @throws DataAccessException for convenience (no try-catch needed)
   * @throws java.sql.SQLException
   */
  public static void main(String[] args) throws DataAccessException, SQLException {

    // add parameters
    if (args.length == 2) {
      args = Arrays.copyOf(args, 3);
      args[2] = "";
    }
    if (args.length != 3) {
      System.err.println("usage: SingleUserTest <url> <login> <password>");
      System.exit(1);
    }

    // execute single-user test suite
    try (DataAccess dao = new DataAccess(args[0], args[1], args[2])) {
      singleUserTests(dao);
    } catch (DataAccessException e) {
      System.err.println("test aborted: " + e);
      e.printStackTrace();
    }

    // print test results
    System.out.println("test results: " + test);

  }

  /**
   * Runs a single-user test suite on the specified data access object.
   *
   * @param dao the data access object to use
   *
   * @throws DataAccessException if anything goes wrong
   */
  private static void singleUserTests(DataAccess dao) throws DataAccessException, SQLException {

    // REMINDER: most of the tests below will fail until the DataAccess methods
    // are implemented.
    //
    // Check the initial state of the data store after it has been initialized by
    // running the DataStoreInit program.
    if (dao.getAvailableSeats(false).size() != SEAT_COUNT
        || !dao.getPriceList().equals(PRICE_LIST)) {
      System.err.print("data store not initialized: ");
      System.err.print("check DataAccess.initDataStore() ");
      System.err.println("and/or run the DataStoreInit program");
      // can't go further
      System.exit(1);
    }

    //
    // 1- Simple bookSeats-getBookings-cancelBookings cycle with no error
    //
    List<Integer> available = dao.getAvailableSeats(false);
    System.out.println("available seats=" + available);
    checkGetAvailableSeats(available, SEAT_COUNT);

    // book one random seat in category #0
    List<Booking> bookings1 = dao.bookSeats("Smith", asList(1), false);
    System.out.println("bookings1=" + bookings1);
    checkBookSeats(bookings1, "Smith", asList(1), false);

    available = dao.getAvailableSeats(false);
    System.out.println("available seats=" + available);
    checkGetAvailableSeats(available, SEAT_COUNT - 1);

    // book another (designated) seat in category #0
    int other = bookings1.get(0).getSeat() % SEAT_COUNT + 1;
    List<Booking> bookings2 = dao.bookSeats("Smith", asList(asList(other)));
    System.out.println("bookings2=" + bookings1);
    checkBookSeats(bookings2, "Smith", asList(asList(other)));

    available = dao.getAvailableSeats(false);
    System.out.println("available seats=" + available);
    checkGetAvailableSeats(available, SEAT_COUNT - 2);

    // cancel all the current bookings
    List<Booking> allBookings = new ArrayList<>();
    allBookings.addAll(bookings1);
    allBookings.addAll(bookings2);
    boolean canceled = dao.cancelBookings(allBookings);
    System.out.println("canceled=" + canceled);
    test.add("cancelBooking", canceled);

    available = dao.getAvailableSeats(false);
    System.out.println("available seats=" + available);
    checkGetAvailableSeats(available, SEAT_COUNT);

    //
    // 2- Erroneous bookings by the same user
    //
    bookings1 = dao.bookSeats("Smith", asList(SEAT_COUNT + 1), false);
    //bookings2 = dao.bookSeats("", asList(1), false);
    // etc.
    //
    //
    // 3- Erroneous bookings by distinct users
    //
    //
    // etc.
  }

  //
  // UTILITIES
  //
  private static void checkGetAvailableSeats(List<Integer> seats, int count) {
    test.add("getAvailableSeats/count", seats.size() == count, 0.5);
    test.add("getAvailableSeats/sorted", isSorted(seats), 0.5);
  }

  private static void checkBookSeats(List<Booking> bookings,
      String customer, List<Integer> counts, boolean adjoining) {

    // check common properties
    int total = counts.stream().mapToInt(Integer::intValue).sum();
    checkBookSeatsCommon(bookings, customer, total);

    // check count by category
    boolean ok = true;
    for (int i = 0; ok && i < counts.size(); i++) {
      int category = i;
      int count = counts.get(category);
      ok = bookings.stream()
          .filter(b -> b.getCategory() == category).count() == count;
    }
    test.add("bookSeats/category.count", ok, 0.20);

    // check adjoining mode
    if (!adjoining) {
      return;
    }
    List<Integer> seats = bookings.stream()
        .map(Booking::getSeat).collect(toList());
    Collections.sort(seats);
    test.add("bookSeats/adjoining", areAdjoining(seats), 1.0);
  }

  private static void checkBookSeats(List<Booking> bookings,
      String customer, List<List<Integer>> seatss) {

    // check common properties
    int total = seatss.stream().mapToInt(List::size).sum();
    checkBookSeatsCommon(bookings, customer, total);

    // check seat numbers by category
    boolean ok = true;
    for (int i = 0; ok && i < seatss.size(); i++) {
      int category = i;
      List<Integer> requestedSeats = seatss.get(category);
      List<Integer> bookedSeats = bookings.stream()
          .filter(b -> b.getCategory() == category)
          .map(Booking::getSeat)
          .collect(toList());
      ok = requestedSeats.equals(bookedSeats);
    }
    test.add("bookSeats/category.seats", ok, 0.20);

  }

  private static void checkBookSeatsCommon(List<Booking> bookings,
      String customer, int total) {

    // check booking count
    test.add("bookSeats/count", bookings.size() == total, 0.20);

    // check customer
    boolean ok = bookings.stream()
        .allMatch(b -> b.getCustomer().equals(customer));
    test.add("bookSeats/customer", ok, 0.20);

    // check price by category
    ok = bookings.stream()
        .allMatch(b -> b.getCategory() < PRICE_LIST.size()
        && b.getPrice() == PRICE_LIST.get(b.getCategory()));
    test.add("bookSeats/price", ok, 0.20);

    // check seat ordering
    List<Integer> seats = bookings.stream()
        .map(Booking::getSeat).collect(toList());
    test.add("bookSeats/sorted", isSorted(seats), 0.20);

  }

  private static boolean isSorted(List<Integer> list) {
    if (list.isEmpty() || list.size() == 1) {
      return true;
    }
    int previous = list.get(0);
    for (int value : list) { // (use iterator for efficiency)
      if (value < previous) {
        return false;
      }
      previous = value;
    }
    return true;
  }

  // Note: list is *sorted*
  private static boolean areAdjoining(List<Integer> list) {
    if (list.isEmpty()) {
      return false;
    }
    int check = list.get(0);
    for (int value : list) {  // (use iterator for efficiency)
      if (value != check) {
        return false;
      }
      check += 1;
    }
    return true;
  }

}
