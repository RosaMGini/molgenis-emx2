package org.molgenis;

import org.molgenis.beans.ColumnAnnotation;

import java.util.UUID;

public class PersonBean {
  private UUID molgenisid;

  @ColumnAnnotation(description = "This is optional first name", nullable = true)
  private String firstName;

  private String lastName;

  private transient String notIncluded;

  public UUID getMolgenisid() {
    return molgenisid;
  }

  public void setMolgenisid(UUID molgenisid) {
    this.molgenisid = molgenisid;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }
}
