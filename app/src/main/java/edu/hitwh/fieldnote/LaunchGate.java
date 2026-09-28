package edu.hitwh.fieldnote;
/** Retained across activity recreation, recreated for a new app opening. */
final class LaunchGate {
 private boolean attempted;
 synchronized boolean take(){if(attempted)return false;attempted=true;return true;}
}
