package model;

//import com.mysql.jdbc.PreparedStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides a generic booking application with high-level methods to access its
 * data store, where the booking information is persisted. The class also hides
 * to the application the implementation of the data store (whether a Relational
 * DBMS, XML files, Excel sheets, etc.) and the complex machinery required to
 * access it (SQL statements, XQuery calls, specific API, etc.).
 * <p>
 * The booking information includes:
 * <ul>
 * <li> A collection of seats (whether theater seats, plane seats, etc.)
 * available for booking. Each seat is uniquely identified by a number in the
 * range {@code [1, seatCount]}. For the sake of simplicity, there is no notion
 * of row and column, as in a theater or a plane: seats are considered
 * <i>adjoining</i> if they bear consecutive numbers.
 * <li> A price list including various categories. For the sake of simplicity,
 * (a) the price of a seat only depends on the customer, e.g. adult, child,
 * retired, etc., not on the location of the seat as in a theater, (b) each
 * price category is uniquely identified by an integer in the range
 * {@code [0, categoryCount)}, e.g. {@code 0}=adult, {@code 1}=child,
 * {@code 2}=retired. (Strings or symbolic constants, like Java {@code enum}s,
 * are not used.)
 * </ul>
 * <p>
 * A typical booking scenario involves the following steps:
 * <ol>
 * <li> The customer books one or more seats, specifying the number of seats
 * requested in each price category with
 * {@link #bookSeats(String, List, boolean)}. He/She lets the system select the
 * seats to book from the currently-available seats.
 * <li> Alternatively, the customer can check the currently-available seats with
 * {@link #getAvailableSeats(boolean)} and then specify the number of the seats
 * he/she wants to book in each price category with
 * {@link #bookSeats(String, List)}.
 * <li> Later on, the customer can change his/her mind and cancel with
 * {@link #cancelBookings(List)} one or more of the bookings he/she previously
 * made.
 * <li> At any time, the customer can check the bookings he/she currently has
 * with {@link #getBookings(String)}.
 * <li> The customer can repeat the above steps any number of times.
 * </ol>
 * <p>
 * The constructor and the methods of this class all throw a
 * {@link DataAccessException} when an unrecoverable error occurs, e.g. the
 * connection to the data store is lost. The methods of this class never return
 * {@code null}. If the return type of a method is {@code List<Item>} and there
 * is no item to return, the method returns the empty list rather than
 * {@code null}.
 * <p>
 * <i>Notes to implementors: </i>
 * <ol>
 * <li> <b>Do not</b> modify the interface of the classes in the {@code model}
 * package. If you do so, the test programs will not compile. Also, remember
 * that the exceptions that a method throws are part of the method's interface.
 * <li> The test programs will abort whenever a {@code DataAccessException} is
 * thrown: make sure your code throws a {@code DataAccessException} only when a
 * severe (i.e. unrecoverable) error occurs.
 * <li> {@code JDBC} often reports constraint violations by throwing an
 * {@code SQLException}: if a constraint violation is intended, make sure your
 * your code does not report it as a {@code DataAccessException}.
 * <li> The implementation of this class must withstand failures (whether
 * client, network of server failures) as well as concurrent accesses to the
 * data store through multiple {@code DataAccess} objects.
 * </ol>
 *
 * @author Jean-Michel Busca
 */
public class DataAccess implements AutoCloseable {

    private Connection connection;

    /**
     * Creates a new {@code DataAccess} object that interacts with the specified
     * data store, using the specified login and the specified password. Each
     * object maintains a dedicated connection to the data store until the
     * {@link close} method is called.
     *
     * @param url the URL of the data store to connect to
     * @param login the (application) login to use
     * @param password the password
     *
     * @throws DataAccessException if an unrecoverable error occurs
     * @throws java.sql.SQLException
     */
    public DataAccess(String url, String login, String password) throws
            DataAccessException, SQLException {
        connection = DriverManager.getConnection(url, login, password);
        connection.setAutoCommit(false);
    }

    /**
     * Initializes the data store according to the specified number of seats and
     * the specified price list. If the data store is already initialized or if
     * it already contains bookings when this method is called, it is reset and
     * initialized again from scratch. When the method completes, the state of
     * the data store is as follows: all the seats are available for booking, no
     * booking is made.
     * <p>
     * <i>Note to implementors: </i>
     * <ol>
     * <li>All the information provided by the parameters must be persisted in
     * the data store. (It must not be kept in Java instance or class
     * attributes.) To enforce this, the data store will be initialized by
     * running the {@code DataStoreInit} program, and then tested by running the
     * {@code SingleUserTest} and {@code MultiUserTest} programs.
     * <li><b>Do not use</b> the statements {@code drop database ...} and
     * {@code create database ...} in this method, as the test program might not
     * have sufficient privileges to execute them. Use the statements {@code drop table
     * ...} and {@code create table ...} instead.
     * <li>The schema of the database (in the sense of "set of tables") is left
     * unspecified. You can select any schema that fulfills the requirements of
     * the {@code DataAccess} methods.
     * </ol>
     *
     * @param seatCount the total number of seats available for booking
     * @param priceList the price for each price category
     *
     * @return {@code true} if the data store was initialized successfully and
     * {@code false} otherwise
     *
     * @throws DataAccessException if an unrecoverable error occurs
     */
    public boolean initDataStore(int seatCount, List<Float> priceList)
            throws DataAccessException, SQLException {
        String sql = null;
        System.out.println("\t\t Initialisation");
        
        try {
            // drop existing tables, if any
            Statement statement = connection.createStatement();
            try {
                statement.executeUpdate("drop table seat");
            } catch (SQLException e) {
                // likely cause: table does not exists: print error and go on
                System.err.print("drop table seat: " + e);
                System.err.println(", going on...");
            }
            try {
                statement.executeUpdate("drop table category");
            } catch (SQLException e) {
                System.err.print("drop table category: " + e);
                System.err.println(", going on...");
            }
            try {
                statement.executeUpdate("drop table booking");
            } catch (SQLException e) {
                System.err.print("drop table booking: " + e);
                System.err.println(", going on...");
            }

            // ...
            // create tables
            sql = "create table seat ("
                    + "id INT NOT NULL AUTO_INCREMENT ,"
                    + "available BOOLEAN NOT NULL DEFAULT TRUE ,"
                    + "PRIMARY KEY (id))";
            statement.executeUpdate(sql);

            sql = "create table booking("
                    + "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "id_seat INT NOT NULL,"
                    + "customer VARCHAR(20) NOT NULL,"
                    + "id_category INT NOT NULL,"
                    + "price FLOAT NOT NULL,"
                    + "FOREIGN KEY(id_seat) REFERENCES seat(id),"
                    + "FOREIGN KEY(id_category) REFERENCES category(id))";
            statement.executeUpdate(sql);

            sql = "create table category ("
                    + "id INT NOT NULL AUTO_INCREMENT ,"
                    + "price FLOAT NOT NULL,"
                    + "PRIMARY KEY (id))";
            statement.executeUpdate(sql);

            //Populate database
            for (int i = 0; i < seatCount; i++) {
                sql = "INSERT INTO seat VALUES ()";
                statement.executeUpdate(sql);
            }

            for (float price : priceList) {
                sql = "INSERT INTO category (price) VALUES ( " + price + " )";
                statement.executeUpdate(sql);

            }

            return true;
        } catch (SQLException e) {
            System.err.println(sql + ": " + e);
            return false;
        }
    }

    /**
     * Returns the price list.
     * <p>
     * <i>Note to implementors: </i>  <br> The method must return the price list
     * persisted in the data store.
     *
     * @return the price list
     *
     * @throws DataAccessException if an unrecoverable error occurs
     * @throws java.sql.SQLException
     */
    public List<Float> getPriceList() throws DataAccessException, SQLException {
        // TODO 
        // select price from prices
        System.out.println("\t\t getprices");
        //PreparedStatement priceList;
        //priceList = connection.prepareStatement("SELECT price FROM category;");
        try (ResultSet results = connection.prepareStatement("SELECT price FROM category;").executeQuery()) {
            List<Float> list = new ArrayList<>();
            while (results.next()) {

                list.add(results.getFloat(1));
            }

            return list;
        }

        //return Collections.EMPTY_LIST;
    }

    /**
     * Returns the available seats in the specified mode. Two modes are
     * provided:
     * <i>stable</i> or not. If the stable mode is selected, the returned seats
     * are guaranteed to remain available until one of the {@code bookSeats}
     * methods is called on this data access object. If the stable mode is not
     * selected, the returned seats might have been booked by another user when
     * one of these methods is called. Regardless of the mode, the available
     * seats are returned in ascending order of number.
     * <p>
     * <i>Note to implementors: </i> <br> The stable mode is defined as an
     * exercise. It cannot be used in a production application as this would
     * prevent all other users from retrieving the available seats until the
     * current user decides which seats to book.
     *
     * @param stable {@code true} to select the stable mode and {@code false}
     * otherwise
     *
     * @return the available seats in ascending order or the empty list if no
     * seat is available
     *
     * @throws DataAccessException if an unrecoverable error occurs
     * @throws java.sql.SQLException
     */
    public List<Integer> getAvailableSeats(boolean stable) throws
            DataAccessException, SQLException {
                System.out.println("\t\t getava");
        // TODO
        //select seat from seats where available=true
        if (stable) {
            //I did not understand how to do that
        } else {
            try (ResultSet results = connection.prepareStatement("SELECT id FROM seat WHERE available=TRUE;").executeQuery()) {
                List<Integer> list = new ArrayList<>();
                while (results.next()) {
                    list.add(results.getInt(1));
                }
                return list;
            }
        }

        return Collections.EMPTY_LIST;
    }

    /**
     * Books the specified number of seats for the specified customer in the
     * specified mode. The number of seats to book is specified for each price
     * category. Two modes are provided: <i>adjoining</i> or not. If the
     * adjoining mode is selected, the method guarantees that the booked seats
     * are adjoining. If the adjoining mode is not selected, the returned seats
     * might be apart. Regardless of the mode, the bookings are returned in
     * ascending order of seat number.
     * <p>
     * If the specified customer already has bookings, the adjoining mode only
     * applies to the new bookings: The method will not try to select seats
     * adjoining to already-booked seats by the same customer.
     * <p>
     * The method executes in an all-or-nothing fashion: if there are not enough
     * available seats left or if the seats are not adjoining while the
     * adjoining mode is selected, then no seat is booked.
     *
     * @param customer the customer who makes the booking
     * @param counts the count of seats to book for each price category:
     * counts.get(i) is the count of seats to book in category #i
     * @param adjoining {@code true} to select the adjoining mode and
     * {@code false} otherwise
     *
     * @return the list of bookings if the booking was successful or the empty
     * list otherwise
     *
     * @throws DataAccessException if an unrecoverable error occurs
     */
    public List<Booking> bookSeats(String customer, List<Integer> counts,
            boolean adjoining) throws DataAccessException, SQLException {

                System.out.println("\t\t bookseats 1");
        List<Booking> listBookings = new ArrayList<>();

        try (ResultSet results = connection.prepareStatement("SELECT id FROM seat WHERE available=TRUE ORDER BY id;").executeQuery()) {
            results.last();
            ArrayList<Integer> freeSeats = new ArrayList();

            if (counts.size() <= results.getRow()) {
                int bookableSeat;
                results.beforeFirst();

                if (adjoining) {
                    while (results.next()) {
                        freeSeats.add(results.getInt(1));
                        if (freeSeats.size() > 2 && results.getInt(1) != freeSeats.get(freeSeats.size() - 1)) {
                            freeSeats.clear();
                            freeSeats.add(results.getInt(1));
                        } else if (freeSeats.size() >= counts.size()) {
                            results.beforeFirst();
                            break;
                        }
                    }
                    if (freeSeats.size() < 2 && counts.size() > 1) {
                        System.err.println("Not enough adjoining seats for this booking");
                        return Collections.EMPTY_LIST;
                    }

                }
                results.next();
                for (int cat : counts) {
                    Statement statement = connection.createStatement();
                    bookableSeat = results.getInt(1);
                    ResultSet price = connection.prepareStatement("SELECT price FROM category WHERE id=" + (cat + 1) + ";").executeQuery();
                    price.next();

                    statement.executeUpdate("INSERT INTO booking (id_seat,customer,id_category,price) "
                            + "VALUES(" + bookableSeat + ",'" + customer + "'," + cat + "," + price.getFloat(1) + ");");
                    statement.executeUpdate("UPDATE seat SET available=FALSE WHERE id=" + bookableSeat + ";");
                    listBookings.add(new Booking(bookableSeat, customer, cat, price.getFloat(1)));
                }
                return listBookings;
            }
            System.err.println("Not enough seats for this booking");

            return Collections.EMPTY_LIST;
        }
        // return Collections.EMPTY_LIST;
    }

    /**
     * Books the specified seats for the specified customer. The seats to book
     * are specified for each price category.
     * <p>
     * The method executes in an all-or-nothing fashion: if one of the specified
     * seats cannot be booked because it is not available, then none of them is
     * booked.
     *
     * @param customer the customer who makes the booking
     * @param seatss the list of seats to book in each price category:
     * seatss.get(i) is the list of seats to book in category #i
     *
     * @return the list of the bookings made by this method call or the empty
     * list if no booking was made
     *
     * @throws DataAccessException if an unrecoverable error occurs
     */
    public List<Booking> bookSeats(String customer, List<List<Integer>> seatss)
            throws DataAccessException, SQLException {
      
                System.out.println("\t\t bookseats");
        List<Booking> listBookings = new ArrayList<>();

        int nbWantedSeats = 0;
        for (List<Integer> category : seatss) {
            for (int nbSeats : category) {
                nbWantedSeats++;
            }
        }

        try (ResultSet results = connection.prepareStatement("SELECT id FROM seat WHERE available=TRUE ORDER BY id;").executeQuery()) {
            results.last();
            ArrayList<Integer> freeSeats = new ArrayList();

            if (nbWantedSeats <= results.getRow()) {
                int bookableSeat;
                results.beforeFirst();
                results.next();

                for (int i = 0; i < seatss.size(); i++) {
                    ResultSet price = connection.prepareStatement("SELECT price FROM category WHERE id=" + (i + 1) + ";").executeQuery();
                    price.next();
                    for (int j = 0; j < seatss.get(i).size(); j++) {
                        Statement statement = connection.createStatement();

                        statement.executeUpdate("INSERT INTO booking (id_seat,customer,id_category,price) "
                                + "VALUES(" + seatss.get(i).get(j) + ",'" + customer + "'," + i + "," + price.getFloat(1) + ");");
                        statement.executeUpdate("UPDATE seat SET available=FALSE WHERE id=" + seatss.get(i).get(j) + ";");
                        listBookings.add(new Booking(seatss.get(i).get(j), customer, i, price.getFloat(1)));
                    }
                }

                return listBookings;
            }
            System.err.println("Not enough seats for this booking");

            return Collections.EMPTY_LIST;
        }
    }

    /**
     * Returns the current bookings of the specified customer. If no customer is
     * specified, the method returns the current bookings of all the customers.
     *
     * @param customer the customer whose bookings must be returned or the empty
     * string {@code ""} if all the bookings must be returned
     *
     * @return the list of the bookings of the specified customer or the empty
     * list if the specified customer does not have any booking
     *
     * @throws DataAccessException if an unrecoverable error occurs
     */
    public List<Booking> getBookings(String customer) throws DataAccessException, SQLException {
                System.out.println("\t\t getbooking");
        ResultSet results;
        List<Booking> bookingList = new ArrayList();
        if (customer.equals("")) {
            results = connection.prepareStatement("SELECT * FROM booking;").executeQuery();
        } else {
            results = connection.prepareStatement("SELECT * FROM booking WHERE customer ='" + customer + "';").executeQuery();
        }
        while (results.next()) {
            bookingList.add(new Booking(results.getInt(1), results.getInt(2), results.getString(3), results.getInt(4), results.getFloat(5)));
        }
        return bookingList;
    }

    /**
     * Cancel the specified bookings. The method checks against the data store
     * that each of the specified bookings is valid, i.e. it is assigned to the
     * specified customer, for the specified price category.
     * <p>
     * The method executes in an all-or-nothing fashion: if one of the specified
     * bookings cannot be canceled because it is not valid, then none of them is
     * canceled.
     *
     * @param bookings the bookings to cancel
     *
     * @return {@code true} if all the bookings were canceled and {@code false}
     * otherwise
     *
     * @throws DataAccessException if an unrecoverable error occurs
     */
    public boolean cancelBookings(List<Booking> bookings) throws DataAccessException, SQLException {
        System.out.println("\t\t cancel");
        boolean sentinel = true;  

        for (Booking b : bookings) {
             System.out.println("\t\t sentinel "+sentinel);
           try (ResultSet results = connection.prepareStatement("SELECT * FROM booking WHERE id_seat ='" + b.getSeat() + "';").executeQuery()){
            results.next();
            if (results.getInt(2) != b.getSeat() || results.getString(3).equals(b.getCustomer()) || results.getInt(4) != b.getCategory() || results.getFloat(5) != b.getPrice()) {
                sentinel = false;
                break;
            }
           }

        }
        if (sentinel) {

            Statement statement = connection.createStatement();
            for (Booking b : bookings) {
                statement.executeUpdate("DELETE FROM booking WHERE id = " + b.getId() + ");");
            }
        }
      
        return sentinel;
    }

    /**
     * Closes this data access object. This closes the underlying connection to
     * the data store and releases all related resources. The application must
     * call this method when it is done using this data access object.
     *
     * @throws DataAccessException if an unrecoverable error occurs
     */
    @Override
    public void close() throws DataAccessException {

        System.out.println("\t\t close");
        try {
            connection.close();
        } catch (SQLException e) {
        }

    }
}
